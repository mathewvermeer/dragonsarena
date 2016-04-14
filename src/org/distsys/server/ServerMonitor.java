package org.distsys.server;

import org.distsys.common.IServer;
import org.distsys.common.concurrent.PingTask;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ServerMonitor implements Monitor {

    private ExecutorService pool;
    private final String host;
    private final IServer server;
    private final List<Listener> listeners;
    private int timeout;

    public ServerMonitor(ExecutorService pool, String host, IServer server) {
        this(pool, host, server, 3);
    }

    public ServerMonitor(ExecutorService pool, String host, IServer server, int timeout) {
        this.pool = pool;
        this.host = host;
        this.server = server;
        this.listeners = new CopyOnWriteArrayList<>();
        this.timeout = timeout;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Future<String> future = pool.submit(new PingTask(server));
                future.get(timeout, TimeUnit.SECONDS);
                Thread.sleep(3000);
            } catch (Exception e) {
                System.out.println(host + " down!");
                informListeners(host);
                break;
            }
        }
    }

    @Override
    public void addListener(Listener l) {
        listeners.add(l);
    }

    @Override
    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    @Override
    public void informListeners(String host) {
        for (Listener l : listeners) {
            l.processAction(host);
        }
    }

}
