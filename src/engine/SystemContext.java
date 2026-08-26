package engine;

import controllers.IServer;
import router.WorkflowRouter;
import metrics.MetricsCollector;
import utils.Params;
import utils.RandomGenerator;

public class SystemContext {
    public IServer serverA;
    public IServer serverB;
    public IServer serverP;

    public WorkflowRouter router;
    public MetricsCollector metrics;
    public RandomGenerator rng;
    public Params params;

    public SystemContext(Params params, RandomGenerator rng, IServer sA, IServer sB, IServer sP) {
        this.params = params;
        this.rng = rng;
        this.serverA = sA;
        this.serverB = sB;
        this.serverP = sP;

        this.metrics = new MetricsCollector();
        this.router = new WorkflowRouter(this);
    }

    public double getNextDepartureTimeA(double clock) { return serverA.getNextCompletionTime(clock); }
    public double getNextDepartureTimeB(double clock) { return serverB.getNextCompletionTime(clock); }
    public double getNextDepartureTimeP(double clock) { return serverP.getNextCompletionTime(clock); }
}