package org.distsys.common.concurrent;

import org.distsys.common.IGameServer;
import org.distsys.common.IServer;

import java.util.concurrent.Callable;

public class PingTask implements Callable<String> {

    private IServer server;

    public PingTask(IServer server) {
        this.server = server;
    }

    @Override
    public String call() throws Exception {
        server.ping();
        return "OK";
    }

}