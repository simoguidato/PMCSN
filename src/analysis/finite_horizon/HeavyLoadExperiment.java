package analysis.finite_horizon;

import controllers.PSServer;
import engine.SimulationEngine;
import engine.SystemContext;
import metrics.BatchMeansAnalyzer;
import metrics.TransientSampler;
import model.ServerState;
import utils.Params;
import utils.RandomGenerator;
import utils.RngSeedLogger;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HeavyLoadExperiment {

    private static final long MASTER_SEED =
            123456789L;

    /*
     * Heavy load:
     *
     * lambda = 1.40 > Xmax = 1 / D_B = 1.25
     */
    private static final double LAMBDA =
            1.40;

    /*
     * Orizzonte finito.
     *
     * Non facciamo warm-up:
     * vogliamo osservare l'evoluzione
     * della congestione da t = 0.
     */
    private static final double HORIZON =
            150_000.0;

    /*
     * Campioniamo le popolazioni
     * ogni 100 secondi.
     */
    private static final double SAMPLE_INTERVAL =
            100.0;

    /*
     * Numero di realizzazioni indipendenti
     * dal punto di vista dei segmenti RNG.
     */
    private static final int NUM_REALIZATIONS =
            50;


    public static void main(String[] args)
            throws Exception {


        int numSamples =
                (int) Math.floor(
                        HORIZON
                                / SAMPLE_INTERVAL
                );


        /*
         * =====================================================
         * DIRECTORY CSV
         * =====================================================
         */

        File csvDir =
                new File("csv");


        if (!csvDir.exists()) {

            boolean created =
                    csvDir.mkdirs();


            if (!created
                    && !csvDir.exists()) {

                throw new IllegalStateException(
                        "Impossibile creare la directory: "
                                + csvDir.getAbsolutePath()
                );
            }
        }


        File resultFile =
                new File(
                        csvDir,
                        "heavy_load_experiment.csv"
                );


        File seedFile =
                new File(
                        csvDir,
                        "heavy_load_experiment_rng_seeds.csv"
                );


        /*
         * RngSeedLogger usa append.
         *
         * All'inizio di una nuova campagna
         * eliminiamo quindi il vecchio file.
         */
        if (seedFile.exists()
                && !seedFile.delete()) {

            throw new IllegalStateException(
                    "Impossibile eliminare il vecchio file RNG: "
                            + seedFile.getAbsolutePath()
            );
        }


        /*
         * =====================================================
         * MATRICE DELLE TRAIETTORIE
         * =====================================================
         */

        double[][] allNA =
                new double
                        [NUM_REALIZATIONS]
                        [numSamples];


        double[][] allNB =
                new double
                        [NUM_REALIZATIONS]
                        [numSamples];


        double[][] allNP =
                new double
                        [NUM_REALIZATIONS]
                        [numSamples];


        /*
         * =====================================================
         * RNG
         * =====================================================
         *
         * UNA SOLA inizializzazione per l'intera
         * configurazione heavy.
         *
         * Il modello nominale utilizza B HyperExp.
         */
        RandomGenerator rng =
                new RandomGenerator(
                        MASTER_SEED,
                        true
                );


        /*
         * =====================================================
         * REALIZZAZIONI
         * =====================================================
         */

        for (int r = 0;
             r < NUM_REALIZATIONS;
             r++) {


            System.out.printf(
                    Locale.US,
                    "%n--- Heavy load: replica %d/%d ---%n",
                    r + 1,
                    NUM_REALIZATIONS
            );


            /*
             * Salviamo lo stato degli stream
             * PRIMA della run.
             *
             * Replica 0:
             * stati derivati dal MASTER_SEED.
             *
             * Replica 1:
             * stati lasciati dalla replica 0.
             *
             * ecc.
             *
             * NON viene effettuato alcun reseed.
             */
            RngSeedLogger.append(
                    seedFile.getPath(),
                    "HeavyLoadExperiment",
                    "1FA_lambda_1.40_HyperExp",
                    r,
                    rng
            );


            /*
             * =================================================
             * PARAMETRI DELLA REALIZZAZIONE
             * =================================================
             */

            Params params =
                    new Params();


            params.lambda =
                    LAMBDA;


            params.is2FA_enabled =
                    false;


            /*
             * Ogni replica deve iniziare con
             * un sistema vuoto.
             *
             * Il RNG invece continua.
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
             * Campionamento del transitorio
             * da t = 0.
             */
            ctx.metrics.enableTransientSampling(
                    SAMPLE_INTERVAL
            );

            SimulationEngine engine =
                    new SimulationEngine(
                            ctx
                    );


            /*
             * =================================================
             * RUN FINITA
             * =================================================
             *
             * Nessun warm-up.
             */
            engine.runForTime(
                    HORIZON
            );


            /*
             * =================================================
             * CAMPIONI
             * =================================================
             */

            TransientSampler ts =
                    ctx.metrics
                            .getTransientSampler();


            List<Double> nA =
                    ts.getNA();


            List<Double> nB =
                    ts.getNB();


            List<Double> nP =
                    ts.getNP();

            if (nA.size() < numSamples
                    || nB.size() < numSamples
                    || nP.size() < numSamples) {

                throw new IllegalStateException(
                        "Campioni transienti insufficienti "
                                + "nella replica "
                                + r
                                + ": NA="
                                + nA.size()
                                + ", NB="
                                + nB.size()
                                + ", NP="
                                + nP.size()
                                + ", attesi="
                                + numSamples
                );
            }


            /*
             * Copiamo ESATTAMENTE numSamples
             * osservazioni.
             */
            for (int k = 0;
                 k < numSamples;
                 k++) {

                allNA[r][k] =
                        nA.get(k);

                allNB[r][k] =
                        nB.get(k);

                allNP[r][k] =
                        nP.get(k);
            }


            if ((r + 1) % 10 == 0) {

                System.out.printf(
                        Locale.US,
                        "Realizzazione %d/%d completata%n",
                        r + 1,
                        NUM_REALIZATIONS
                );
            }
        }


        /*
         * =====================================================
         * STATISTICHE TRA LE REALIZZAZIONI
         * =====================================================
         *
         * Per ogni istante:
         *
         * t = 100, 200, ..., 150000
         *
         * 50 osservazioni,
         * una per realizzazione.
         *
         * Calcoliamo quindi media + IC 95%.
         */

        try (FileWriter fw =
                     new FileWriter(resultFile)) {


            fw.write(
                    "t,"
                            + "N_A_mean,N_A_hw,"
                            + "N_B_mean,N_B_hw,"
                            + "N_P_mean,N_P_hw\n"
            );


            for (int k = 0;
                 k < numSamples;
                 k++) {


                double t =
                        (k + 1)
                                * SAMPLE_INTERVAL;


                List<Double> sampleA =
                        new ArrayList<>();


                List<Double> sampleB =
                        new ArrayList<>();


                List<Double> sampleP =
                        new ArrayList<>();


                for (int r = 0;
                     r < NUM_REALIZATIONS;
                     r++) {

                    sampleA.add(
                            allNA[r][k]
                    );

                    sampleB.add(
                            allNB[r][k]
                    );

                    sampleP.add(
                            allNP[r][k]
                    );
                }


                BatchMeansAnalyzer
                        .ConfidenceInterval ciA =

                        BatchMeansAnalyzer
                                .computeCI(
                                        sampleA
                                );


                BatchMeansAnalyzer
                        .ConfidenceInterval ciB =

                        BatchMeansAnalyzer
                                .computeCI(
                                        sampleB
                                );


                BatchMeansAnalyzer
                        .ConfidenceInterval ciP =

                        BatchMeansAnalyzer
                                .computeCI(
                                        sampleP
                                );


                fw.write(
                        String.format(
                                Locale.US,

                                "%.1f,"
                                        + "%.6f,%.6f,"
                                        + "%.6f,%.6f,"
                                        + "%.6f,%.6f%n",

                                t,

                                ciA.mean,
                                ciA.halfWidth,

                                ciB.mean,
                                ciB.halfWidth,

                                ciP.mean,
                                ciP.halfWidth
                        )
                );
            }
        }

        System.out.println(
                "\nHeavy load completato."
        );


        System.out.println(
                "Risultati: "
                        + resultFile.getAbsolutePath()
        );


        System.out.println(
                "Seed RNG: "
                        + seedFile.getAbsolutePath()
        );
    }
}