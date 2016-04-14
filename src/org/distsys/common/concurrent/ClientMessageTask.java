package org.distsys.common.concurrent;

import org.distsys.common.MessageSocket;
import org.distsys.common.messages.Message;

import java.io.IOException;
import java.io.ObjectOutputStream;

public class ClientMessageTask implements Runnable {

    private Message message;
    private MessageSocket messageSocket;

    public ClientMessageTask(Message message, MessageSocket messageSocket) {
        this.message = message;
        this.messageSocket = messageSocket;
    }

    @Override
    public void run() {
        try {
            ObjectOutputStream out = messageSocket.getObjectOutputStream();
            synchronized (out) {
                out.writeObject(message);
                out.flush();
            }
        } catch (IOException e) {
            try {
                messageSocket.getSocket().close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }
}
