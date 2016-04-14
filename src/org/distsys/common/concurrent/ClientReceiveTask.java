package org.distsys.common.concurrent;

import org.distsys.common.IGameServer;
import org.distsys.common.MessageSocket;
import org.distsys.common.das.BattleField;
import org.distsys.common.messages.Message;

import java.io.*;
import java.net.SocketTimeoutException;

public class ClientReceiveTask implements Runnable {

    private final MessageSocket messageSocket;
    private IGameServer server;

    public ClientReceiveTask(MessageSocket messageSocket, IGameServer server) {
        this.messageSocket = messageSocket;
        this.server = server;
    }

    @Override
    public void run() {
		ObjectInputStream objectInputStream = messageSocket.getObjectInputStream();
        while (true) {
            try {
                Message message = (Message) objectInputStream.readObject();
                server.receiveMessage(message);
            } catch (SocketTimeoutException e) {
//                try {
//                    Thread.sleep(10);
//                } catch (InterruptedException e1) {
//                    e1.printStackTrace();
//                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                break;
            }
        }
    }
}
