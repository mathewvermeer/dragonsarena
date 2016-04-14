package org.distsys.common.concurrent;

import org.distsys.common.IGameServer;
import org.distsys.common.messages.Message;

import java.util.concurrent.Callable;

public class RequestVoteTask implements Callable<Boolean> {

    private Message message;
    private IGameServer server;

    public RequestVoteTask(Message message, IGameServer server) {
        this.message = message;
        this.server = server;
    }

    @Override
    public Boolean call() throws Exception {
        boolean b;
        try {
            b = server.requestVote(message);
        } catch (Exception e) {
            b = false;
        }
        return b;
    }
}