package org.distsys.common.concurrent;

import org.distsys.common.IGameServer;
import org.distsys.common.messages.Message;

import java.util.concurrent.Callable;

public class AbortTask implements Callable<Void> {

    private Message message;
    private IGameServer server;

    public AbortTask(Message message, IGameServer server) {
        this.message = message;
        this.server = server;
    }

    @Override
    public Void call() throws Exception {
        server.abort(message);
        return null;
    }
}