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

    /** Istante corrente dell'orologio di simulazione (utile per calcolare le medie a fine run). */
    public double getClock() {
        return clock;
    }

    public void run(long maxJobs) {
        System.out.println("Avvio Simulazione PS...");

        while (ctx.metrics.getTotalJobsCompleted() < maxJobs) {

            double nextDepA = ctx.getNextDepartureTimeA(clock);
            double nextDepB = ctx.getNextDepartureTimeB(clock);
            double nextDepP = ctx.getNextDepartureTimeP(clock);

            double nextEventTime = Math.min(nextArrival, Math.min(nextDepA, Math.min(nextDepB, nextDepP)));

            ctx.metrics.updateAreas(clock, nextEventTime, ctx.serverA.size(), ctx.serverB.size(), ctx.serverP.size());
            updateServers(clock, nextEventTime, nextDepA, nextDepB, nextDepP);

            clock = nextEventTime;

            if (clock == nextArrival) {
                handleArrival();
            } else if (clock == nextDepA) {
                ctx.metrics.recordDeparture(ServerId.SERVER_A, clock);
                ctx.router.routeJob(ctx.serverA.popCompletedJob(), ServerId.SERVER_A, clock);
            } else if (clock == nextDepB) {
                ctx.metrics.recordDeparture(ServerId.SERVER_B, clock);
                ctx.router.routeJob(ctx.serverB.popCompletedJob(), ServerId.SERVER_B, clock);
            } else if (clock == nextDepP) {
                ctx.metrics.recordDeparture(ServerId.SERVER_P, clock);
                ctx.router.routeJob(ctx.serverP.popCompletedJob(), ServerId.SERVER_P, clock);
            }
        }
    }

    private void updateServers(double startTs, double endTs, double depA, double depB, double depP) {
        try {
            Double respA = (endTs == depA) ? (endTs - ctx.serverA.getMinRemainingLifeJob().getStationEntryTime()) : null;
            ctx.serverA.computeJobsAdvancement(startTs, endTs, respA);

            Double respB = (endTs == depB) ? (endTs - ctx.serverB.getMinRemainingLifeJob().getStationEntryTime()) : null;
            ctx.serverB.computeJobsAdvancement(startTs, endTs, respB);
            if (respB != null) {
                ctx.metrics.recordServerBVisit(respB);
            }

            Double respP = (endTs == depP) ? (endTs - ctx.serverP.getMinRemainingLifeJob().getStationEntryTime()) : null;
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
    /**
     * Esegue la simulazione fino al raggiungimento di un orizzonte temporale prefissato.
     * Necessario per l'analisi del transitorio (TransientAnalysis).
     *
     * @param maxTime L'orizzonte temporale massimo (es. 60_000 secondi)
     */
    public void runForTime(double maxTime) {
        System.out.println("Avvio Simulazione PS (Orizzonte temporale: " + maxTime + "s)...");

        while (clock < maxTime) {
            double nextDepA = ctx.getNextDepartureTimeA(clock);
            double nextDepB = ctx.getNextDepartureTimeB(clock);
            double nextDepP = ctx.getNextDepartureTimeP(clock);

            // Identifica il prossimo evento temporale
            double nextEventTime = Math.min(nextArrival, Math.min(nextDepA, Math.min(nextDepB, nextDepP)));

            // Se il prossimo evento supera l'orizzonte temporale, fermati
            if (nextEventTime > maxTime) {
                ctx.metrics.updateAreas(clock, maxTime, ctx.serverA.size(), ctx.serverB.size(), ctx.serverP.size());
                updateServers(clock, maxTime, nextDepA, nextDepB, nextDepP);
                clock = maxTime;
                break;
            }

            // Aggiorna le statistiche e lo stato dei server
            ctx.metrics.updateAreas(clock, nextEventTime, ctx.serverA.size(), ctx.serverB.size(), ctx.serverP.size());
            updateServers(clock, nextEventTime, nextDepA, nextDepB, nextDepP);

            // Avanza il clock
            clock = nextEventTime;

            // Processa l'evento corrispondente al clock attuale
            if (clock == nextArrival) {
                handleArrival();
            } else if (clock == nextDepA) {
                ctx.metrics.recordDeparture(ServerId.SERVER_A, clock);
                ctx.router.routeJob(ctx.serverA.popCompletedJob(), ServerId.SERVER_A, clock);
            } else if (clock == nextDepB) {
                ctx.metrics.recordDeparture(ServerId.SERVER_B, clock);
                ctx.router.routeJob(ctx.serverB.popCompletedJob(), ServerId.SERVER_B, clock);
            } else if (clock == nextDepP) {
                ctx.metrics.recordDeparture(ServerId.SERVER_P, clock);
                ctx.router.routeJob(ctx.serverP.popCompletedJob(), ServerId.SERVER_P, clock);
            }
        }
    }
}