package org.distsys.common;

import java.rmi.RemoteException;
import java.util.Map;

public interface IMatchmakingServer extends IServer {
    String register(String hostname) throws RemoteException;
    Map<String, String> getServers() throws RemoteException;
}
