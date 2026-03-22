package engine;

import model.Job;
import model.JobClass;
import model.ServerId;

public class SimulationEngine {
    private SystemContext ctx;
    private double clock = 0.0;
    private double nextArrival;

    public SimulationEngine(SystemContext ctx) {
        this.ctx = ctx;
        this.nextArrival = ctx.rng.getInterarrivalTime(ctx.params.lambda);
    }

    public void run(long maxJobs) {
        System.out.println("Avvio Simulazione PS...");

        while (ctx.metrics.getTotalJobsCompleted() < maxJobs) {

            double nextDepA = ctx.getNextDepartureTimeA(clock);
            double nextDepB = ctx.getNextDepartureTimeB(clock);
            double nextDepP = ctx.getNextDepartureTimeP(clock);

            double nextEventTime = Math.min(nextArrival, Math.min(nextDepA, Math.min(nextDepB, nextDepP)));
            double deltaT = nextEventTime - clock;

            ctx.metrics.updateAreas(deltaT, ctx.serverA.size(), ctx.serverB.size(), ctx.serverP.size());
            updateServers(clock, nextEventTime, nextDepA, nextDepB, nextDepP);

            clock = nextEventTime;

            if (clock == nextArrival) {
                handleArrival();
            } else if (clock == nextDepA) {
                ctx.router.routeJob(ctx.serverA.popCompletedJob(), ServerId.SERVER_A, clock);
            } else if (clock == nextDepB) {
                ctx.router.routeJob(ctx.serverB.popCompletedJob(), ServerId.SERVER_B, clock);
            } else if (clock == nextDepP) {
                ctx.router.routeJob(ctx.serverP.popCompletedJob(), ServerId.SERVER_P, clock);
            }
        }
    }

    private void updateServers(double startTs, double endTs, double depA, double depB, double depP) {
        try {
            Double respA = (endTs == depA) ? (endTs - ctx.serverA.getMinRemainingLifeJob().getArrivalTime()) : null;
            ctx.serverA.computeJobsAdvancement(startTs, endTs, respA);

            Double respB = (endTs == depB) ? (endTs - ctx.serverB.getMinRemainingLifeJob().getArrivalTime()) : null;
            ctx.serverB.computeJobsAdvancement(startTs, endTs, respB);

            Double respP = (endTs == depP) ? (endTs - ctx.serverP.getMinRemainingLifeJob().getArrivalTime()) : null;
            ctx.serverP.computeJobsAdvancement(startTs, endTs, respP);
        } catch (Exception e) {
            System.err.println("Errore PS: " + e.getMessage());
        }
    }

    private void handleArrival() {
        nextArrival = clock + ctx.rng.getInterarrivalTime(ctx.params.lambda);

        double mean = ctx.params.getMeanServiceTime_A(JobClass.CLASS_1);
        double demand = ctx.rng.getServiceTimeA(mean);

        Job newJob = new Job(clock, JobClass.CLASS_1, demand);
        ctx.serverA.addJob(newJob);
    }
}
