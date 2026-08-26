package controllers;

import model.ServerState;

public class PSServer extends AbstractServer {
    public PSServer(double capacity, ServerState serverState, int index) {
        super(capacity, serverState, index);
    }
}