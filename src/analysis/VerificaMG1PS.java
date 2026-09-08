package analysis;

import controllers.PSServer;
import libs.Rngs;
import metrics.BatchMeansAnalyzer;
import model.Job;
import model.JobClass;
import model.ServerState;
import model.distribution.Distribution;
import model.distribution.Exp;
import model.distribution.HyperExp;

import java.util.ArrayList;
import java.util.List;

public class VerificaMG1PS {

    private static final long SEED = 42L;
    private static final int NUM_BATCHES = 64;
    private static final int BATCH_SIZE = 512;

    /*
     * Capacità del server = 1 job/s.
     * Quindi mu = 1.
     */
    private static final double MU = 1.0;
    private static final double MEAN_SERVICE = 1.0 / MU;


    public static void main(String[] args) {

        runExperiment(
                0.60,
                false,
                1.0
        );

        runExperiment(
                0.80,
                false,
                1.0
        );

        runExperiment(
                0.60,
                true,
                4.0
        );

        runExperiment(
                0.80,
                true,
                4.0
        );
    }


    private static void runExperiment(
            double lambda,
            boolean hyperExp,
            double cv
    ) {

        String distributionName =
                hyperExp
                        ? "H2 (CV=" + cv + ")"
                        : "Exp";


        System.out.println();
        System.out.println(
                "=========================================="
        );

        System.out.printf(
                "M/G/1/PS - lambda=%.2f - servizio=%s%n",
                lambda,
                distributionName
        );

        System.out.println(
                "=========================================="
        );

        Rngs rngs =
                new Rngs();

        rngs.plantSeeds(SEED);


        /*
         * Processo di arrivo M:
         * inter-arrivi esponenziali.
         */
        Distribution arrivals =
                new Exp(
                        rngs,
                        0
                );


        /*
         * Distribuzione di servizio.
         *
         * Exp:
         *   stream 1
         *
         * H2:
         *   stream 1 = Bernoulli
         *   stream 2 = fase 1
         *   stream 3 = fase 2
         */
        Distribution service;

        if (hyperExp) {

            service =
                    new HyperExp(
                            rngs,
                            1,
                            2,
                            3,
                            cv
                    );

        } else {

            service =
                    new Exp(
                            rngs,
                            1
                    );
        }

        PSServer server =
                new PSServer(
                        1.0,
                        ServerState.IDLE,
                        0
                );


        /*
         * Medie dei 64 batch.
         */
        List<Double> batchR =
                new ArrayList<>();

        List<Double> batchN =
                new ArrayList<>();

        List<Double> batchU =
                new ArrayList<>();


        double clock = 0.0;

        double nextArrival =
                arrivals.generate(
                        1.0 / lambda
                );


        int nextJobId = 1;

        long completedJobs = 0;


        /*
         * Statistiche del batch corrente.
         */
        double batchStartTime = 0.0;

        double batchAreaN = 0.0;

        double batchBusyTime = 0.0;

        double batchResponseSum = 0.0;

        int completedInBatch = 0;


        long totalToComplete =
                (long) NUM_BATCHES
                        * BATCH_SIZE;


        while (completedJobs
                < totalToComplete) {


            double nextDeparture =
                    server.getNextCompletionTime(
                            clock
                    );


            double nextEventTime =
                    Math.min(
                            nextArrival,
                            nextDeparture
                    );


            double delta =
                    nextEventTime
                            - clock;


            if (delta < 0.0) {

                throw new IllegalStateException(
                        "Clock non monotono"
                );
            }


            /*
             * Area di N(t) nel batch corrente.
             */
            batchAreaN +=
                    server.size()
                            * delta;


            /*
             * Busy time.
             */
            if (server.size() > 0) {

                batchBusyTime +=
                        delta;
            }


            /*
             * Facciamo avanzare il clock
             * virtuale del Processor Sharing.
             */
            server.advanceVirtualTime(
                    delta
            );


            clock =
                    nextEventTime;


            /*
             * =========================
             * ARRIVO
             * =========================
             */
            if (nextArrival
                    <= nextDeparture) {


                double demand =
                        service.generate(
                                MEAN_SERVICE
                        );


                Job job =
                        new Job(
                                nextJobId++,
                                clock,
                                JobClass.CLASS_1,
                                demand
                        );


                server.addJob(
                        job
                );


                nextArrival =
                        clock
                                + arrivals.generate(
                                1.0 / lambda
                        );


                /*
                 * =========================
                 * COMPLETAMENTO
                 * =========================
                 */
            } else {


                Job completed =
                        server.popCompletedJob();


                double responseTime =
                        clock
                                - completed
                                .getArrivalTime();


                batchResponseSum +=
                        responseTime;


                completedJobs++;

                completedInBatch++;


                /*
                 * Raggiunti 512 completamenti:
                 * termina il batch.
                 */
                if (completedInBatch
                        == BATCH_SIZE) {


                    double batchDuration =
                            clock
                                    - batchStartTime;


                    /*
                     * E[T]
                     */
                    batchR.add(
                            batchResponseSum
                                    / BATCH_SIZE
                    );


                    /*
                     * E[N]:
                     *
                     * integrale N(t) / durata.
                     */
                    batchN.add(
                            batchAreaN
                                    / batchDuration
                    );


                    /*
                     * rho / utilizzazione:
                     *
                     * tempo occupato / durata.
                     */
                    batchU.add(
                            batchBusyTime
                                    / batchDuration
                    );


                    /*
                     * Reset del batch.
                     */
                    batchStartTime =
                            clock;

                    batchAreaN =
                            0.0;

                    batchBusyTime =
                            0.0;

                    batchResponseSum =
                            0.0;

                    completedInBatch =
                            0;
                }
            }
        }


        /*
         * =================================
         * INTERVALLI DI CONFIDENZA 95%
         * =================================
         */

        BatchMeansAnalyzer.ConfidenceInterval ciR =
                BatchMeansAnalyzer.computeCI(
                        batchR
                );


        BatchMeansAnalyzer.ConfidenceInterval ciN =
                BatchMeansAnalyzer.computeCI(
                        batchN
                );


        BatchMeansAnalyzer.ConfidenceInterval ciU =
                BatchMeansAnalyzer.computeCI(
                        batchU
                );


        /*
         * =================================
         * VALORI TEORICI M/G/1/PS
         * =================================
         *
         * rho = lambda / mu
         *
         * E[T] = 1 / (mu - lambda)
         *
         * E[N] = lambda * E[T]
         */

        double theoreticalU =
                lambda / MU;


        double theoreticalR =
                1.0
                        / (MU - lambda);


        double theoreticalN =
                lambda
                        * theoreticalR;


        /*
         * =================================
         * OUTPUT
         * =================================
         */

        System.out.println(
                "\n--- Utilizzazione rho ---"
        );

        printComparison(
                theoreticalU,
                ciU
        );


        System.out.println(
                "\n--- Tempo medio di risposta E[T] ---"
        );

        printComparison(
                theoreticalR,
                ciR
        );


        System.out.println(
                "\n--- Popolazione media E[N] ---"
        );

        printComparison(
                theoreticalN,
                ciN
        );


        System.out.printf(
                "%nClock finale = %.6f%n",
                clock
        );

        System.out.printf(
                "Job completati = %d%n",
                completedJobs
        );

        System.out.printf(
                "Batch prodotti = %d%n",
                batchR.size()
        );
    }


    private static void printComparison(
            double theoretical,
            BatchMeansAnalyzer.ConfidenceInterval ci
    ) {

        double relativeError =
                Math.abs(
                        ci.mean
                                - theoretical
                )
                        / theoretical
                        * 100.0;


        boolean insideCI =
                theoretical
                        >= ci.getLower()
                        &&
                        theoretical
                                <= ci.getUpper();


        System.out.printf(
                "Teorico       = %.6f%n",
                theoretical
        );


        System.out.printf(
                "Simulato      = %.6f ± %.6f%n",
                ci.mean,
                ci.halfWidth
        );


        System.out.printf(
                "IC95%%         = [%.6f, %.6f]%n",
                ci.getLower(),
                ci.getUpper()
        );


        System.out.printf(
                "Errore relativo= %.4f%%%n",
                relativeError
        );


        System.out.printf(
                "Teorico dentro IC95%% = %s%n",
                insideCI
                        ? "SI"
                        : "NO"
        );
    }
}
