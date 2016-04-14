package org.distsys.common.das;

import org.distsys.common.messages.Message;

/**
 * Created by Mathew on 1/3/2016.
 */
public interface IMessageReceivedHandler {
    void processMessage(Message message);
}
