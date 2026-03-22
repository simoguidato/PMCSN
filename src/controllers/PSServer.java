package controllers;

import model.Job;
import model.ServerState;

public class PSServer extends AbstractServer {

    public PSServer(double capacity, ServerState serverState, int index) {
        super(capacity, serverState, index);
    }

    @Override
    public Job popCompletedJob() {
        Job completed = getMinRemainingLifeJob();
        // Rimuoviamo il job completato dalla coda
        this.jobs.remove(completed);
        return completed;
    }
}
