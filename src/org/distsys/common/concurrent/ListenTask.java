package org.distsys.common.concurrent;

import org.distsys.common.IGameServer;
import org.distsys.common.MessageSocket;
import org.distsys.common.das.BattleField;
import org.distsys.common.messages.Message;
import org.distsys.server.ClientMonitor;
import org.distsys.server.GameServer;
import org.distsys.server.ServerMonitor;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class ListenTask implements Runnable {

    private ServerSocket serverSocket;
    private Map<String, MessageSocket> clients;
    private ExecutorService pool;
    private IGameServer server;

    public ListenTask(ExecutorService pool, Map<String, MessageSocket> clients, IGameServer server) {
        this.pool = pool;
        this.server = server;
        try {
            this.serverSocket = new ServerSocket(33333);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        this.clients = clients;
    }

    @Override
    public void run() {
        Socket socket;
        InputStream in;
        BufferedReader reader;
        if (GameServer.isMaster.get()) {
            while(GameServer.isMaster.get()) {
                try {
                    socket = serverSocket.accept();
                    MessageSocket messageSocket = new MessageSocket(socket);

					Message message = new Message();
					String id = "D" + BattleField.getBattleField().getNewUnitID();
					message.put("id", id);
					pool.execute(new ClientMessageTask(message, messageSocket));

                    pool.execute(new ClientReceiveTask(messageSocket, server));
                } catch (SocketTimeoutException e) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        } else {
            while (!GameServer.isMaster.get()) {
                try {
                    socket = serverSocket.accept();
                    socket.setSoTimeout(5000);
                    MessageSocket messageSocket = new MessageSocket(socket);
                    in = socket.getInputStream();
                    reader = new BufferedReader(new InputStreamReader(in));

                    String id = reader.readLine();
                    clients.put(id, messageSocket);
                    System.out.println(id + " CONNECTED");
                    ClientMonitor monitor = new ClientMonitor(id, messageSocket);
                    monitor.addListener(new ClientListener(monitor));
                    new Thread(monitor).start();
                } catch (SocketTimeoutException e) {
                    System.out.println("timeout");
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }

        for (MessageSocket s : clients.values()) {
            System.out.println("REMOVING CLIENTS");
            try {
                s.getSocket().close();
            } catch (IOException ignored) {}
        }

        clients.clear();
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        GameServer.isListening.set(false);
    }

    private class ClientListener implements ServerMonitor.Listener {
        private ClientMonitor monitor;

        private ClientListener(ClientMonitor monitor) {
            this.monitor = monitor;
        }

        @Override
        public void processAction(String id) {
            clients.remove(id);
            monitor.removeListener(this);
        }
    }

}
