package org.distsys.server;

import org.distsys.common.EC2Server;
import org.distsys.common.IGameServer;
import org.distsys.common.IMatchmakingServer;

import java.io.*;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MatchmakingServer extends EC2Server implements IMatchmakingServer {

    private final Map<String, IGameServer> servers;
    private final static Object lock = new Object();

    private volatile String master;

    private MatchmakingServer() throws Exception {
        super();
        servers = new ConcurrentHashMap<>();
        listenForClients();
    }

    @Override
    public String register(final String host) throws RemoteException {
        if (master == null) {
            master = host;
        }

        if (!servers.containsKey(host)) {
            final IGameServer server;
            try {
                server = (IGameServer) Naming.lookup("rmi://" + host);
            } catch (NotBoundException | MalformedURLException e) {
                throw new RemoteException("Could not connect to " + host);
            }
            servers.put(host, server);

            ServerMonitor monitor = new ServerMonitor(pool, host, server);
            monitor.addListener(new GameServerListener(monitor));
            new Thread(monitor).start();
        }

        return master;
    }

    @Override
    public Map<String, String> getServers() throws RemoteException {
        Map<String, String> map = new HashMap<>();
        map.put("master", master.replace("/server", ""));
        String slave = null;
        int min = Integer.MAX_VALUE;
        for (String s : servers.keySet()) {
            if (master.equals(s)) continue;
            int i = servers.get(s).numberOfClients();
            if (i < min) {
                min = i;
                slave = s;
            }
        }
        if (slave == null) return null;
        map.put("slave", slave.replace("/server", ""));
        return map;
    }

    private void listenForClients() {
        pool.execute(() -> {
            ServerSocket serverSocket = null;
            try {
                serverSocket = new ServerSocket(44444);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            Socket socket;
            OutputStream out;
            ObjectOutputStream writer;
            while (true) {
                try {
                    socket = serverSocket.accept();
                    out = socket.getOutputStream();
                    writer = new ObjectOutputStream(out);
                    writer.writeObject(getServers());
                    writer.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private class GameServerListener implements ServerMonitor.Listener {
        private ServerMonitor monitor;

        private GameServerListener(ServerMonitor monitor) {
            this.monitor = monitor;
        }

        @Override
        public void processAction(String hostDown) {
            servers.remove(hostDown);

            if (servers.isEmpty()) return;
            if (!master.equals(hostDown)) return;

            int min = Integer.MAX_VALUE;
            String newMasterHost = null;
            IGameServer newMasterServer = null;
            for (String s : servers.keySet()) {
                int current;
                IGameServer currentServer = servers.get(s);
                try {
                    current = currentServer.numberOfClients();
                } catch (RemoteException e) {
                    e.printStackTrace();
                    continue;
                }
                if (current < min) {
                    min = current;
                    newMasterHost = s;
                    newMasterServer = currentServer;
                    continue;
                }
                newMasterHost = s;
                newMasterServer = currentServer;
            }

            if (newMasterHost == null) return;

            reconfigureMaster(newMasterHost, newMasterServer);

            monitor.removeListener(this);
        }

        private void reconfigureMaster(String newMasterHost, IGameServer newMasterServer) {
            try {
                HashSet<String> slaves = new HashSet<>();
                slaves.addAll(
                        servers
                                .keySet()
                                .stream()
                                .filter(s -> !s.equals(newMasterHost))
                                .collect(Collectors.toList())
                );

                master = newMasterHost;
                newMasterServer.setAsMaster(true);

                for (String s : slaves) {
                    newMasterServer.registerSlave(s);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Registry reg = LocateRegistry.createRegistry(1099);
        MatchmakingServer server = new MatchmakingServer();
        reg.rebind("server", server);
        System.out.println("Server is running.");
    }
}
