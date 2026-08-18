package controllers;

import model.Job;
import model.ServerState;

import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

// NOTA: Assicurati di avere le tue classi JobList e ServerStats nel posto giusto!
import model.JobList;
import metrics.ServerStats; // Adegua l'import in base a dove hai messo ServerStats

public abstract class AbstractServer implements IServer {
    private final DecimalFormat f;
    private ServerState serverState;
    protected double capacity;
    protected JobList jobs;
    protected ServerStats stats;
    private Deque<Double> movingWindowResponseTime;
    private static final int SLIDING_WINDOW_SIZE = 1000;

    public AbstractServer(double capacity, ServerState serverState, int index) {
        this.serverState = serverState;
        this.capacity = capacity;
        this.jobs = new JobList();
        this.stats = new ServerStats(index); // Se richiede index
        this.movingWindowResponseTime = new ArrayDeque<>();

        this.f = (DecimalFormat) DecimalFormat.getInstance(Locale.US);
        this.f.applyPattern("###0.00000000");
    }

    public ServerState getServerState() { return serverState; }
    public void setServerState(ServerState serverState) { this.serverState = serverState; }
    public double getCapacity() { return capacity; }
    public ServerStats getStats() { return stats; }

    @Override
    public void computeJobsAdvancement(double startTs, double endTs, Double completedJobResponseTime) throws Exception {
        // Il job in completamento (se presente) è già dentro 'jobs' a questo punto
        // (viene rimosso solo dopo, con popCompletedJob() chiamato dal motore).
        // Quindi jobs.size() conta già correttamente tutti gli N job che si dividono
        // la capacità nell'intervallo [startTs, endTs]: NON va sommato di nuovo.
        int jobAdvanced = jobs.size();

        if (completedJobResponseTime != null) {
            if(this.movingWindowResponseTime.size() == SLIDING_WINDOW_SIZE) {
                this.movingWindowResponseTime.removeFirst();
            }
            this.movingWindowResponseTime.addLast(completedJobResponseTime);
        }

        if (jobAdvanced > 0) {
            double quantum = (this.capacity / jobAdvanced) * (endTs - startTs);
            // Assumiamo che JobList estenda Iterable<Job> o abbia un metodo getJobs()
            for (Job job : this.jobs.getJobs()) {
                job.decreaseRemainingLife(quantum);
            }
        }
    }

    @Override
    public void addJob(Job job) { jobs.add(job); }

    @Override
    public boolean activeJobExists() { return jobs.activeJobExists(); }

    @Override
    public int size() { return jobs.size(); }

    @Override
    public double getMinRemainingLife() { return jobs.minRemainingLife(); }

    @Override
    public Job getMinRemainingLifeJob() { return jobs.getMinRemainingLifeJob(); }

    // Lasciamo questo metodo astratto affinché lo implementi il PSServer
    @Override
    public abstract Job popCompletedJob();
}