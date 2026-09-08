package analysis;

import controllers.PSServer;
import engine.SimulationEngine;
import engine.SystemContext;
import metrics.TransientSampler;
import model.ServerState;
import utils.Params;
import utils.RandomGenerator;

import java.io.FileWriter;
import java.util.List;

public class TransientAnalysis {

    private static final long MASTER_SEED = 123456789L;

    private static final double SAMPLE_INTERVAL = 100.0;

    private static final int NUM_REPLICHE = 64;


    public static void main(String[] args) throws Exception {

        /*
         * Punto critico:
         * lambda = 1.20 -> rho_B = 0.96.
         *
         * Serve soprattutto per determinare il warm-up
         * da usare nelle analisi a regime.
         */
        runTransientAnalysis(
                1.20,
                150_000,
                "csv/transient_analysis_lambda1.2.csv"
        );

        runTransientAnalysis(
                0.50,
                150_000,
                "csv/transient_analysis_lambda0.5.csv"
        );
    }


    private static void runTransientAnalysis(
            double lambda,
            double horizon,
            String outPath
    ) throws Exception {

        System.out.printf(
                "%n=== Analisi del transitorio, lambda = %.2f ===%n",
                lambda
        );


        int numSamples =
                (int) Math.floor(horizon / SAMPLE_INTERVAL);

        double[][] allNA =
                new double[NUM_REPLICHE][numSamples];

        double[][] allNB =
                new double[NUM_REPLICHE][numSamples];

        double[][] allNP =
                new double[NUM_REPLICHE][numSamples];


        /*
         *
         * l'RNG viene inizializzato UNA SOLA VOLTA
         * per questa configurazione.
         *
         * Le realizzazioni successive continuano
         * dagli stati lasciati dalle precedenti.
         */
        RandomGenerator rng =
                new RandomGenerator(MASTER_SEED);


        /*
         * Memorizziamo lo stato iniziale degli stream
         * di ciascuna realizzazione.
         */
        long[][] runSeeds =
                new long
                        [NUM_REPLICHE]
                        [RandomGenerator.TOTAL_STREAMS_USED];


        for (int r = 0; r < NUM_REPLICHE; r++) {

            /*
             * Fotografia degli stream PRIMA
             * di effettuare qualsiasi estrazione
             * relativa alla nuova realizzazione.
             */
            runSeeds[r] =
                    rng.snapshotUsedStreamSeeds();


            Params params =
                    new Params();

            params.lambda = lambda;
            params.is2FA_enabled = false;


            /*
             * Lo stato del sistema viene invece
             * ricreato da zero ad ogni realizzazione.
             */
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


            /*
             * Campionamento temporale integrato
             * ogni 100 secondi.
             */
            ctx.metrics.enableTransientSampling(
                    SAMPLE_INTERVAL
            );


            SimulationEngine engine =
                    new SimulationEngine(ctx);


            engine.runForTime(horizon);


            TransientSampler ts =
                    ctx.metrics.getTransientSampler();


            List<Double> nA = ts.getNA();
            List<Double> nB = ts.getNB();
            List<Double> nP = ts.getNP();


            int m =
                    Math.min(
                            numSamples,
                            Math.min(
                                    nA.size(),
                                    Math.min(
                                            nB.size(),
                                            nP.size()
                                    )
                            )
                    );


            for (int k = 0; k < m; k++) {

                allNA[r][k] = nA.get(k);
                allNB[r][k] = nB.get(k);
                allNP[r][k] = nP.get(k);
            }


            System.out.printf(
                    "Realizzazione %d/%d completata: "
                            + "%d job, clock finale = %.1f%n",
                    r + 1,
                    NUM_REPLICHE,
                    ctx.metrics.getTotalJobsCompleted(),
                    engine.getClock()
            );
        }


        /*
         * =============================
         * TRAIETTORIE
         * =============================
         */

        String trajectoriesPath =
                outPath.replace(
                        ".csv",
                        "_trajectories.csv"
                );


        try (FileWriter fw =
                     new FileWriter(trajectoriesPath)) {

            StringBuilder header =
                    new StringBuilder("t");


            for (int r = 1;
                 r <= NUM_REPLICHE;
                 r++) {

                header
                        .append(",N_B_")
                        .append(r);
            }


            header.append(",N_B_mean\n");

            fw.write(header.toString());


            for (int k = 0;
                 k < numSamples;
                 k++) {

                double t =
                        (k + 1) * SAMPLE_INTERVAL;


                double meanB = 0.0;


                StringBuilder line =
                        new StringBuilder();

                line.append(t);


                for (int r = 0;
                     r < NUM_REPLICHE;
                     r++) {

                    line
                            .append(",")
                            .append(allNB[r][k]);

                    meanB += allNB[r][k];
                }


                meanB /= NUM_REPLICHE;


                line
                        .append(",")
                        .append(meanB)
                        .append("\n");


                fw.write(line.toString());
            }
        }


        /*
         * =============================
         * SEED DEGLI STREAM
         * =============================
         */

        String seedPath =
                outPath.replace(
                        ".csv",
                        "_rng_seeds.csv"
                );


        try (FileWriter fw =
                     new FileWriter(seedPath)) {

            StringBuilder header =
                    new StringBuilder("replica");


            for (int stream = 0;
                 stream <
                         RandomGenerator.TOTAL_STREAMS_USED;
                 stream++) {

                header
                        .append(",stream_")
                        .append(stream);
            }


            header.append("\n");

            fw.write(header.toString());


            for (int r = 0;
                 r < NUM_REPLICHE;
                 r++) {

                StringBuilder line =
                        new StringBuilder(
                                Integer.toString(r)
                        );


                for (int stream = 0;
                     stream <
                             RandomGenerator.TOTAL_STREAMS_USED;
                     stream++) {

                    line
                            .append(",")
                            .append(
                                    runSeeds[r][stream]
                            );
                }


                line.append("\n");

                fw.write(line.toString());
            }
        }


        System.out.println(
                "Traiettorie esportate: "
                        + trajectoriesPath
        );

        System.out.println(
                "Seed RNG esportati: "
                        + seedPath
        );
    }
}