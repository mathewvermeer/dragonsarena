package org.distsys.common.concurrent;

import org.distsys.common.IGameServer;
import org.distsys.common.messages.Message;

import java.rmi.RemoteException;

public class ServerMessageTask implements Runnable {

    private Message message;
    private IGameServer server;

    public ServerMessageTask(Message message, IGameServer server) {
        this.message = message;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            server.receiveMessage(message);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}