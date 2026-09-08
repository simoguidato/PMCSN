package analysis;

import controllers.PSServer;
import engine.SimulationEngine;
import engine.SystemContext;
import model.ServerState;
import utils.Params;
import utils.RandomGenerator;

public class VerificaInvarianti {

    private static final long SEED =
            42L;

    /*
     * Non serve una campagna enorme.
     * 200000 completamenti sono sufficienti
     * per attraversare un numero molto elevato
     * di eventi.
     */
    private static final long COMPLETIONS =
            200_000L;


    public static void main(String[] args) {

        verificaScenario(
                0.60
        );

        verificaScenario(
                1.20
        );
    }


    private static void verificaScenario(
            double lambda
    ) {

        System.out.printf(
                "%n=================================%n"
                        + "Verifica invarianti - lambda=%.2f%n"
                        + "=================================%n",
                lambda
        );


        Params params =
                new Params();

        params.lambda =
                lambda;

        params.is2FA_enabled =
                false;


        /*
         * Usiamo B esponenziale perché in questa
         * fase vogliamo verificare il motore,
         * non studiare l'effetto della H2.
         */
        RandomGenerator rng =
                new RandomGenerator(
                        SEED,
                        false
                );


        PSServer serverA =
                new PSServer(
                        1.0,
                        ServerState.IDLE,
                        0
                );

        PSServer serverB =
                new PSServer(
                        1.0,
                        ServerState.IDLE,
                        1
                );

        PSServer serverP =
                new PSServer(
                        1.0,
                        ServerState.IDLE,
                        2
                );


        SystemContext ctx =
                new SystemContext(
                        params,
                        rng,
                        serverA,
                        serverB,
                        serverP
                );


        SimulationEngine engine =
                new SimulationEngine(
                        ctx
                );


        engine.run(
                COMPLETIONS
        );


        long arrivals =
                engine.getTotalArrivals();


        long completed =
                ctx.metrics
                        .getTotalJobsCompleted();


        long nA =
                ctx.serverA.size();

        long nB =
                ctx.serverB.size();

        long nP =
                ctx.serverP.size();


        long inSystem =
                nA + nB + nP;


        /*
         * Invariante fondamentale:
         *
         * ogni job arrivato o:
         *
         * 1. ha completato il workflow;
         * 2. si trova in A;
         * 3. si trova in B;
         * 4. si trova in P.
         */
        long rhs =
                completed
                        + inSystem;


        boolean conservationPass =
                arrivals == rhs;


        System.out.println(
                "\n--- Conservazione dei job ---"
        );


        System.out.printf(
                "Arrivi esterni       = %d%n",
                arrivals
        );

        System.out.printf(
                "Job completati       = %d%n",
                completed
        );

        System.out.printf(
                "Job in A             = %d%n",
                nA
        );

        System.out.printf(
                "Job in B             = %d%n",
                nB
        );

        System.out.printf(
                "Job in P             = %d%n",
                nP
        );

        System.out.printf(
                "Completati + presenti= %d%n",
                rhs
        );


        System.out.printf(
                "Conservazione        = %s%n",
                conservationPass
                        ? "PASS"
                        : "FAIL"
        );


        /*
         * =============================
         * CLOCK
         * =============================
         */

        System.out.println(
                "\n--- Verifica del clock ---"
        );


        System.out.printf(
                "Clock finale         = %.6f%n",
                engine.getClock()
        );


        System.out.printf(
                "Eventi processati    = %d%n",
                engine.getProcessedEvents()
        );


        System.out.printf(
                "Eventi a delta t = 0 = %d%n",
                engine.getZeroDeltaEvents()
        );


        boolean strictClockPass =
                engine.getZeroDeltaEvents()
                        == 0;


        System.out.printf(
                "Clock strettamente crescente = %s%n",
                strictClockPass
                        ? "PASS"
                        : "DA CONTROLLARE"
        );


        /*
         * =============================
         * SANITY CHECK METRICHE
         * =============================
         */

        double clock =
                engine.getClock();


        double NA =
                ctx.metrics
                        .getAverageJobsInSystemA(
                                clock
                        );

        double NB =
                ctx.metrics
                        .getAverageJobsInSystemB(
                                clock
                        );

        double NP =
                ctx.metrics
                        .getAverageJobsInSystemP(
                                clock
                        );


        boolean metricsPass =
                Double.isFinite(NA)
                        && Double.isFinite(NB)
                        && Double.isFinite(NP)
                        && NA >= 0.0
                        && NB >= 0.0
                        && NP >= 0.0;


        System.out.printf(
                "%nMetriche finite/non negative = %s%n",
                metricsPass
                        ? "PASS"
                        : "FAIL"
        );


        if (!conservationPass) {

            throw new AssertionError(
                    "Violazione della conservazione "
                            + "dei job"
            );
        }


        if (!strictClockPass) {

            throw new AssertionError(
                    "Sono stati osservati eventi "
                            + "senza avanzamento del clock"
            );
        }


        if (!metricsPass) {

            throw new AssertionError(
                    "Metriche non valide"
            );
        }


        System.out.println(
                "\nVERIFICA COMPLETATA: PASS"
        );
    }
}