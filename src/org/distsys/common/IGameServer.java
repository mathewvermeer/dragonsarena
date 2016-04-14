package org.distsys.common;

import org.distsys.common.das.BattleField;
import org.distsys.common.messages.Message;

import java.rmi.RemoteException;

public interface IGameServer extends IServer {
    void setAsMaster(boolean b) throws RemoteException;

    void receiveMessage(Message message) throws RemoteException;

    int numberOfClients() throws RemoteException;

//    void register(String host) throws RemoteException;

    void unregister(String host) throws RemoteException;

    int numberOfSlaves() throws RemoteException;

    void registerSlave(String host) throws RemoteException;

    //2PC methods:

    boolean requestVote(Message message) throws RemoteException;

    void commit(Message message) throws RemoteException;

    void commit(BattleField battleField) throws RemoteException;

    void abort(Message message) throws RemoteException;
}
