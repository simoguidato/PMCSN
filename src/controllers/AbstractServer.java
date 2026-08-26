package controllers;

import metrics.ServerStats;
import model.Job;
import model.ServerState;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Implementazione del Processor Sharing basata su tag di servizio virtuale: ogni server tiene
 * un unico contatore S (tempo di servizio virtuale, che avanza a velocità capacità/n finché n>0);
 * ad ogni job, all'ammissione, viene assegnato un tag di completamento F = S + demand. Il job con
 * F minimo è sempre il prossimo a completare, ed è sempre in cima alla min-heap: nessun ciclo su
 * tutti i job ad ogni evento (costo O(log n) invece di O(n) per evento).
 */
public abstract class AbstractServer implements IServer {
    private ServerState serverState;
    protected double capacity;
    protected double virtualClock = 0.0;
    protected PriorityQueue<Job> activeJobs;
    protected ServerStats stats;

    public AbstractServer(double capacity, ServerState serverState, int index) {
        this.serverState = serverState;
        this.capacity = capacity;
        this.activeJobs = new PriorityQueue<>(Comparator.comparingDouble(Job::getFinishTag));
        this.stats = new ServerStats(index);
    }

    public ServerState getServerState() { return serverState; }
    public void setServerState(ServerState serverState) { this.serverState = serverState; }
    public double getCapacity() { return capacity; }
    public ServerStats getStats() { return stats; }

    @Override
    public void addJob(Job job) {
        job.setFinishTag(virtualClock + job.getDemand());
        activeJobs.add(job);
    }

    @Override
    public void advanceVirtualTime(double delta) {
        int n = activeJobs.size();
        if (n > 0) {
            virtualClock += (capacity / n) * delta;
        }
    }

    @Override
    public double getNextCompletionTime(double now) {
        if (activeJobs.isEmpty()) return Double.POSITIVE_INFINITY;
        double gap = Math.max(0.0, activeJobs.peek().getFinishTag() - virtualClock);
        double realDelay = gap * (activeJobs.size() / capacity);
        return now + realDelay;
    }

    @Override
    public Job popCompletedJob() {
        return activeJobs.poll();
    }

    @Override
    public int size() {
        return activeJobs.size();
    }
}