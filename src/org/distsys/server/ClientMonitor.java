package org.distsys.server;

import org.distsys.common.MessageSocket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientMonitor implements Monitor {

    private final String id;
    private final MessageSocket messageSocket;
    private final List<Listener> listeners;
    private long lastReadTime;
    private BufferedReader reader;

    public ClientMonitor(String id, MessageSocket messageSocket) {
        this.id = id;
        this.messageSocket = messageSocket;
        listeners = new CopyOnWriteArrayList<>();
        try {
            InputStreamReader in = new InputStreamReader(messageSocket.getSocket().getInputStream());
            reader = new BufferedReader(in);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                reader.read();
                lastReadTime = System.currentTimeMillis();
                Thread.sleep(2000);
            } catch (SocketTimeoutException e) {
//                if (!isConnectionAlive()) {
//                    System.out.println(id + " down!");
//                    informListeners(id);
//                    break;
//                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            } catch (IOException | InterruptedException e) {
                break;
            }
        }
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isConnectionAlive() {
        int maxTimeout = 10000;
        return System.currentTimeMillis() - lastReadTime < maxTimeout;
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
    public void informListeners(String id) {
        for (Listener l : listeners) {
            l.processAction(id);
        }
    }

}
