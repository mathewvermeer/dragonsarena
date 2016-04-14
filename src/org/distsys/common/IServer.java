package org.distsys.common;

import org.distsys.common.messages.Message;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IServer extends Remote {
    default void ping() throws RemoteException {}
}
