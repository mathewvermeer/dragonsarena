package org.distsys.server;

import org.distsys.common.EC2Server;
import org.distsys.common.IGameServer;
import org.distsys.common.IMatchmakingServer;
import org.distsys.common.MessageSocket;
import org.distsys.common.concurrent.AbortTask;
import org.distsys.common.concurrent.ClientMessageTask;
import org.distsys.common.concurrent.ListenTask;
import org.distsys.common.concurrent.RequestVoteTask;
import org.distsys.common.das.BattleField;
import org.distsys.common.das.units.Unit;
import org.distsys.common.messages.Message;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class GameServer extends EC2Server implements IGameServer {

	private final static String mmServer = "ec2-52-28-215-173.eu-central-1.compute.amazonaws.com";
	public static AtomicBoolean isMaster;
	public static AtomicBoolean isListening;

	private Map<String, MessageSocket> clients;
	private Map<String, IGameServer> slaves;
	private Message currentMessage;
	private AtomicBoolean readyToCommit;
	private BattleField battleField;

	private GameServer() throws Exception {
		super();
		clients = new ConcurrentHashMap<>();
		slaves = new ConcurrentHashMap<>();
		readyToCommit = new AtomicBoolean(false);
		isMaster = new AtomicBoolean(false);
		isListening = new AtomicBoolean(false);
		battleField = BattleField.getBattleField();
	}

	public static void main(String[] args) throws Exception {
		Registry reg = LocateRegistry.createRegistry(1099);
		GameServer server = new GameServer();
		reg.rebind("server", server);

		IMatchmakingServer matchmakingServer = (IMatchmakingServer) Naming.lookup("rmi://" + mmServer + "/server");
		String masterHostname = matchmakingServer.register(hostname);
		isMaster.set(hostname.equals(masterHostname));

		if (!isMaster.get()) {
			IGameServer master = (IGameServer) Naming.lookup("rmi://" + masterHostname);
			master.registerSlave(hostname);
			System.out.println("Connected to master: " + masterHostname);

		}

		if (!isListening.get()) {
			isListening.set(true);
			server.listenForClients();
		}

		System.out.println("Server is running.");
	}

	@Override
	public void setAsMaster(boolean b) throws RemoteException {
		isMaster.set(b);

		if (!isListening.get()) {
			listenForClients();
		}
	}

	private void listenForClients() {
		pool.execute(new ListenTask(pool, clients, this));
	}

	protected final Object lock = new Object();

	@Override
	public void receiveMessage(Message message) throws RemoteException {
		synchronized (lock) {
			AtomicBoolean abort = new AtomicBoolean(false);
			ArrayList<Future<Boolean>> votes = new ArrayList<>();
			for (IGameServer server : slaves.values()) {
				Future<Boolean> vote = pool.submit(new RequestVoteTask(message, server));
				votes.add(vote);
			}

			AtomicInteger numberDone = new AtomicInteger(0);

			for (Future<Boolean> vote : votes) {
				pool.execute(() -> {
					try {
						vote.get(10000, TimeUnit.MILLISECONDS);
					} catch (InterruptedException | ExecutionException | TimeoutException e) {
						abort.set(true);
					}
					numberDone.getAndIncrement();
				});
			}

			while (numberDone.get() != slaves.size()) {
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			if (abort.get()) {
				this.abort(message);
				for (IGameServer server : slaves.values()) {
					pool.submit(new AbortTask(message, server));
				}

				return;
			}

			this.currentMessage = message;
			this.commit(message);
//        battleField.processMessage(message);
			for (IGameServer server : slaves.values()) {
				try {
					server.commit(message);
//                server.commit(battleField);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	public int numberOfClients() throws RemoteException {
		return clients.size();
	}

	@Override
	public void unregister(String host) throws RemoteException {
		clients.remove(host);
	}

	@Override
	public int numberOfSlaves() throws RemoteException {
		return slaves.size();
	}

	@Override
	public void registerSlave(String host) throws RemoteException {
		pool.execute(() -> {
			try {
				IGameServer slave = (IGameServer) Naming.lookup("rmi://" + host);
				slave.setAsMaster(false);
				slaves.put(host, slave);
				System.out.println(slaves.size() + " -> " + host + " joined!");
			} catch (NotBoundException | MalformedURLException | RemoteException e) {
				e.printStackTrace();
			}
		});
	}

	@Override
	public boolean requestVote(Message message) throws RemoteException {
		if (this.currentMessage != null && !this.readyToCommit.get()) return false;

		this.currentMessage = message;
		this.readyToCommit.set(false);

		pool.execute(() -> {
			try {
				Thread.sleep(10000);
				if (!this.readyToCommit.get()) {
					abort(message);
					//OR search for last updated & update completely
					//OR search for new master & update ??
				}
			} catch (InterruptedException | RemoteException e) {
				e.printStackTrace();
			}
		});

		return !isMaster.get();
	}

	@Override
	public void commit(Message message) throws RemoteException {
		if (this.currentMessage == null) return;
		this.readyToCommit.set(true);
		System.out.println("COMMITING " + message);

		//update battlefield
		battleField.processMessage(message);

		sendToClients(message);
//		sendToClients(reply);
//        sendToClients(battleField);
		this.currentMessage = null;
	}

	@Override
	public void commit(BattleField battleField) throws RemoteException {
		if (this.currentMessage == null) return;
		this.readyToCommit.set(true);
		System.out.println("COMMITING BATTLEFIELD");

		this.battleField.updateState(battleField);

		//update battlefield
		sendToClients(battleField);
		this.currentMessage = null;
	}

	private void sendToClients(BattleField battleField) {
		for (MessageSocket client : clients.values()) {
			pool.execute(() -> {
				ObjectOutputStream out = client.getObjectOutputStream();
				try {
					synchronized (out) {
						out.writeObject(battleField);
						out.flush();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		}
	}

	private void sendToClients(Message message) throws RemoteException {
//        if (message == null) return;
//        MessageRequest request = (MessageRequest) message.get("request");
//        if (request == null) {
//			//FIX LATER
//			if(message.size() == 1) return;
//			//
//            Integer unitId = (Integer) message.get("origin");
//            MessageSocket client = clients.get("D" + unitId);
//            if (client == null) return;
//            ClientMessageTask task = new ClientMessageTask(message, client);
//            pool.execute(task);
//            return;
//        }
//
//		if(request == MessageRequest.getType || request == MessageRequest.getUnit)
//			return;

		for (Map.Entry<String, MessageSocket> entry : clients.entrySet()) {
			MessageSocket client = entry.getValue();
			ClientMessageTask task = new ClientMessageTask(message, client);
			pool.execute(task);
		}
	}

	@Override
	public void abort(Message message) throws RemoteException {
		System.out.println("ABORTING " + message);
		this.currentMessage = null;
		this.readyToCommit.set(false);
	}

}
