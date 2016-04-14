package org.distsys.client;

import org.distsys.common.messages.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.rmi.RemoteException;
import java.util.Map;

/**
 * @author Mathew : 5/4/2016.
 */
public class Test {

    private final static String mmServer = "ec2-52-28-215-173.eu-central-1.compute.amazonaws.com";

    public static void main(String[] args) {
        try {
            Socket matchMakingSocket = new Socket(mmServer, 44444);
            ObjectInputStream mmIn = new ObjectInputStream(matchMakingSocket.getInputStream());
            Map<String, String> servers = (Map<String, String>) mmIn.readObject();
            matchMakingSocket.close();
            if (servers == null) throw new RemoteException();

            Socket masterSocket = new Socket(servers.get("master"), 33333);
            ObjectOutputStream out = new ObjectOutputStream(masterSocket.getOutputStream());
            Message message = new Message();
            message.put("test", "test");
            out.writeObject(message);
            out.flush();

            masterSocket.close();
            matchMakingSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
