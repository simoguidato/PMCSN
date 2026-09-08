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

public class HeavyExpVsHyperExpComparison {

    private static final long MASTER_SEED =
            123456789L;

    private static final double LAMBDA =
            1.40;

    private static final double HORIZON =
            150_000.0;

    private static final double SAMPLE_INTERVAL =
            100.0;

    private static final int NUM_REALIZATIONS =
            30;


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


        /*
         * false -> B Exp
         * true  -> B HyperExp
         *
         * Sono due configurazioni DISTINTE.
         */
        for (boolean hyperExp :
                new boolean[]{false, true}) {


            String label =
                    hyperExp
                            ? "HyperExp"
                            : "Exp";


            System.out.printf(
                    Locale.US,

                    "%n========================================%n"
                            + "Heavy Load - Server B: %s%n"
                            + "lambda = %.2f%n"
                            + "scenario = 1FA%n"
                            + "realizzazioni = %d%n"
                            + "========================================%n",

                    label,
                    LAMBDA,
                    NUM_REALIZATIONS
            );


            File resultFile =
                    new File(
                            csvDir,
                            "heavy_"
                                    + label
                                    + ".csv"
                    );


            File seedFile =
                    new File(
                            csvDir,
                            "heavy_"
                                    + label
                                    + "_rng_seeds.csv"
                    );


            /*
             * Logger in append:
             * puliamo eventuale vecchia campagna.
             */
            if (seedFile.exists()
                    && !seedFile.delete()) {

                throw new IllegalStateException(
                        "Impossibile eliminare il vecchio file RNG: "
                                + seedFile.getAbsolutePath()
                );
            }


            /*
             * =================================================
             * NUOVA CONFIGURAZIONE RNG
             * =================================================
             *
             * Exp riparte dal master seed.
             *
             * HyperExp riparte anch'essa dal
             * master seed.
             *
             * Dentro ciascuna configurazione,
             * invece, le repliche continuano
             * sul medesimo RNG.
             */
            RandomGenerator rng =
                    new RandomGenerator(
                            MASTER_SEED,
                            hyperExp
                    );


            /*
             * Una traiettoria N_B per replica.
             */
            double[][] allNB =
                    new double
                            [NUM_REALIZATIONS]
                            [numSamples];


            /*
             * =================================================
             * REALIZZAZIONI
             * =================================================
             */

            for (int r = 0;
                 r < NUM_REALIZATIONS;
                 r++) {


                /*
                 * Stato iniziale effettivo
                 * della replica.
                 *
                 * Nessun reseed.
                 */
                RngSeedLogger.append(
                        seedFile.getPath(),
                        "HeavyExpVsHyperExpComparison",
                        "1FA_lambda_1.40_" + label,
                        r,
                        rng
                );


                Params params =
                        new Params();


                params.lambda =
                        LAMBDA;


                params.is2FA_enabled =
                        false;


                /*
                 * Ogni realizzazione parte
                 * con un sistema nuovo/vuoto.
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


                ctx.metrics.enableTransientSampling(
                        SAMPLE_INTERVAL
                );
                SimulationEngine engine =
                        new SimulationEngine(
                                ctx
                        );


                /*
                 * Heavy load:
                 * nessun warm-up.
                 */
                engine.runForTime(
                        HORIZON
                );


                TransientSampler ts =
                        ctx.metrics
                                .getTransientSampler();


                List<Double> nB =
                        ts.getNB();

                if (nB.size()
                        < numSamples) {

                    throw new IllegalStateException(
                            "Campioni N_B insufficienti "
                                    + "nella replica "
                                    + r
                                    + " della configurazione "
                                    + label
                                    + ": disponibili="
                                    + nB.size()
                                    + ", attesi="
                                    + numSamples
                    );
                }


                for (int k = 0;
                     k < numSamples;
                     k++) {

                    allNB[r][k] =
                            nB.get(k);
                }


                if ((r + 1) % 10 == 0) {

                    System.out.printf(
                            Locale.US,
                            "%s - realizzazione %d/%d completata%n",
                            label,
                            r + 1,
                            NUM_REALIZATIONS
                    );
                }
            }


            /*
             * =================================================
             * RISULTATI
             * =================================================
             *
             * A ogni istante si calcola
             * media e IC 95% di N_B
             * sulle 30 realizzazioni.
             */

            try (FileWriter fw =
                         new FileWriter(resultFile)) {


                fw.write(
                        "t,N_B_mean,N_B_hw\n"
                );


                for (int k = 0;
                     k < numSamples;
                     k++) {


                    double t =
                            (k + 1)
                                    * SAMPLE_INTERVAL;


                    List<Double> sample =
                            new ArrayList<>();


                    for (int r = 0;
                         r < NUM_REALIZATIONS;
                         r++) {

                        sample.add(
                                allNB[r][k]
                        );
                    }


                    BatchMeansAnalyzer
                            .ConfidenceInterval ci =

                            BatchMeansAnalyzer
                                    .computeCI(
                                            sample
                                    );


                    fw.write(
                            String.format(
                                    Locale.US,

                                    "%.1f,%.6f,%.6f%n",

                                    t,
                                    ci.mean,
                                    ci.halfWidth
                            )
                    );
                }
            }


            System.out.println(
                    "Risultati "
                            + label
                            + ": "
                            + resultFile.getAbsolutePath()
            );


            System.out.println(
                    "Seed "
                            + label
                            + ": "
                            + seedFile.getAbsolutePath()
            );
        }


        System.out.println(
                "\nConfronto heavy Exp vs HyperExp completato."
        );
    }
}