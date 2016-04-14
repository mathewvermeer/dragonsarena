package org.distsys.server;

public interface Monitor extends Runnable {
    interface Listener {
        void processAction(String s);
    }

    void addListener(Listener l);

    void removeListener(Listener l);

    void informListeners(String s);
}
