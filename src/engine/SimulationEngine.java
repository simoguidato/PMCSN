package engine;

import model.Job;
import model.JobClass;
import model.ServerId;

public class SimulationEngine {

    private SystemContext ctx;

    private double clock = 0.0;
    private double nextArrival;

    private int nextJobId = 1;

    /*
     * Variabili usate esclusivamente per la verifica.
     */
    private long processedEvents = 0;
    private long zeroDeltaEvents = 0;

    private static final double CLOCK_EPS = 1e-12;


    public SimulationEngine(SystemContext ctx) {

        this.ctx = ctx;

        this.nextArrival =
                ctx.rng.getInterarrivalTime(
                        ctx.params.lambda
                );
    }


    public double getClock() {
        return clock;
    }


    /*
     * Numero totale di job arrivati dall'esterno.
     *
     * nextJobId parte da 1 e viene incrementato
     * una volta per ogni arrivo.
     */
    public long getTotalArrivals() {
        return nextJobId - 1L;
    }


    /*
     * Job presenti in questo momento
     * nell'intera rete.
     */
    public long getJobsInSystem() {

        return (long) ctx.serverA.size()
                + ctx.serverB.size()
                + ctx.serverP.size();
    }


    public long getProcessedEvents() {
        return processedEvents;
    }


    /*
     * Numero di eventi che si sono verificati
     * allo stesso istante del precedente.
     *
     * Per questo modello con distribuzioni
     * continue ci aspettiamo zero.
     */
    public long getZeroDeltaEvents() {
        return zeroDeltaEvents;
    }


    public void run(long maxJobs) {

        System.out.println(
                "Avvio Simulazione PS "
                        + "(tag di servizio virtuale)..."
        );

        while (ctx.metrics.getTotalJobsCompleted()
                < maxJobs) {

            stepEvent(
                    Double.POSITIVE_INFINITY
            );
        }
    }


    public void runForTime(double horizon) {

        System.out.println(
                "Avvio Simulazione PS "
                        + "(tag virtuale, orizzonte = "
                        + horizon
                        + "s)..."
        );

        while (clock < horizon) {

            if (stepEvent(horizon)) {
                break;
            }
        }
    }


    private boolean stepEvent(
            double horizonCap
    ) {

        double nextDepA =
                ctx.getNextDepartureTimeA(
                        clock
                );

        double nextDepB =
                ctx.getNextDepartureTimeB(
                        clock
                );

        double nextDepP =
                ctx.getNextDepartureTimeP(
                        clock
                );


        double nextEventTime =
                Math.min(
                        nextArrival,
                        Math.min(
                                nextDepA,
                                Math.min(
                                        nextDepB,
                                        nextDepP
                                )
                        )
                );


        /*
         * =============================
         * VERIFICA DEL CLOCK
         * =============================
         *
         * Un evento non può mai trovarsi
         * nel passato rispetto al clock.
         */
        if (nextEventTime < clock - CLOCK_EPS) {

            throw new IllegalStateException(
                    "Clock non monotono: "
                            + "clock="
                            + clock
                            + ", nextEventTime="
                            + nextEventTime
            );
        }
        if (Math.abs(
                nextEventTime - clock
        ) <= CLOCK_EPS) {

            zeroDeltaEvents++;
        }


        /*
         * Orizzonte temporale raggiunto:
         * non processiamo l'evento successivo.
         */
        if (nextEventTime > horizonCap) {

            double delta =
                    horizonCap - clock;


            ctx.metrics.updateAreas(
                    clock,
                    horizonCap,

                    ctx.serverA.size(),
                    ctx.serverB.size(),
                    ctx.serverP.size()
            );


            advanceVirtualTimes(
                    delta
            );


            clock =
                    horizonCap;


            return true;
        }


        double delta =
                nextEventTime - clock;


        ctx.metrics.updateAreas(
                clock,
                nextEventTime,

                ctx.serverA.size(),
                ctx.serverB.size(),
                ctx.serverP.size()
        );


        advanceVirtualTimes(
                delta
        );


        clock =
                nextEventTime;


        /*
         * Da qui in poi stiamo effettivamente
         * processando un evento.
         */
        processedEvents++;


        if (clock == nextArrival) {

            handleArrival();

        } else if (clock == nextDepA) {

            Job completed =
                    ctx.serverA
                            .popCompletedJob();


            completed.leaveServer(
                    clock
            );


            ctx.metrics.recordDeparture(
                    ServerId.SERVER_A,
                    clock
            );


            ctx.router.routeJob(
                    completed,
                    ServerId.SERVER_A,
                    clock
            );

        } else if (clock == nextDepB) {

            Job completed =
                    ctx.serverB
                            .popCompletedJob();


            completed.leaveServer(
                    clock
            );


            ctx.metrics.recordServerBVisit(
                    clock
                            - completed
                            .getStationEntryTime()
            );


            ctx.metrics.recordDeparture(
                    ServerId.SERVER_B,
                    clock
            );


            ctx.router.routeJob(
                    completed,
                    ServerId.SERVER_B,
                    clock
            );

        } else if (clock == nextDepP) {

            Job completed =
                    ctx.serverP
                            .popCompletedJob();


            completed.leaveServer(
                    clock
            );


            ctx.metrics.recordDeparture(
                    ServerId.SERVER_P,
                    clock
            );


            ctx.router.routeJob(
                    completed,
                    ServerId.SERVER_P,
                    clock
            );
        }


        return false;
    }


    private void advanceVirtualTimes(
            double delta
    ) {

        ctx.serverA.advanceVirtualTime(
                delta
        );

        ctx.serverB.advanceVirtualTime(
                delta
        );

        ctx.serverP.advanceVirtualTime(
                delta
        );
    }


    private void handleArrival() {

        nextArrival =
                clock
                        + ctx.rng
                        .getInterarrivalTime(
                                ctx.params.lambda
                        );


        double mean =
                ctx.params
                        .getMeanServiceTime_A(
                                JobClass.CLASS_1
                        );


        double demand =
                ctx.rng
                        .getServiceTimeA(
                                mean
                        );


        Job newJob =
                new Job(
                        nextJobId++,
                        clock,
                        JobClass.CLASS_1,
                        demand
                );


        newJob.enterServer(
                "A",
                clock
        );


        ctx.serverA.addJob(
                newJob
        );
    }
}