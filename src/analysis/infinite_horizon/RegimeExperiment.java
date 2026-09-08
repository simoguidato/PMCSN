package analysis.infinite_horizon;

import controllers.PSServer;
import engine.SimulationEngine;
import engine.SystemContext;
import metrics.BatchMeansAnalyzer;
import metrics.TimeBatchCollector;
import model.ServerState;
import utils.Params;
import utils.RandomGenerator;
import utils.RngSeedLogger;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RegimeExperiment {

    /*
     * Master seed fisso dell'intera campagna.
     *
     * Ogni NUOVA configurazione viene
     * reinizializzata da questo master seed.
     */
    private static final long MASTER_SEED =
            123456789L;


    /*
     * Valori determinati tramite:
     *
     * - analisi del transitorio
     * - metodo di Welch
     * - autocorrelazione
     */
    private static final double WARMUP_TIME =
            100_000.0;

    private static final int BATCH_SIZE =
            8000;

    private static final int NUM_BATCHES =
            100;


    public static void main(String[] args)
            throws Exception {


        /*
         * 100 batch × 8000 osservazioni.
         */
        long jobsAfterWarmup =
                (long) BATCH_SIZE
                        * NUM_BATCHES;


        /*
         * Valori di lambda analizzati.
         */
        double[] lambdas = {

                0.50,
                0.55,
                0.60,
                0.65,
                0.70,
                0.75,
                0.80,
                0.85,
                0.90,
                0.95,
                1.00,
                1.05,
                1.10,
                1.15,
                1.20
        };


        /*
         * false -> 1FA
         * true  -> 2FA
         */
        boolean[] scenarios2FA = {

                false,
                true
        };

        String outPath =
                "csv/regime_experiment.csv";

        String seedOutPath =
                "csv/regime_rng_seeds.csv";


        File outFile =
                new File(outPath);

        File seedFile =
                new File(seedOutPath);
        File parent =
                outFile.getParentFile();


        if (parent != null
                && !parent.exists()) {


            boolean created =
                    parent.mkdirs();


            if (!created
                    && !parent.exists()) {


                throw new IllegalStateException(
                        "Impossibile creare la directory: "
                                + parent.getAbsolutePath()
                );
            }
        }


        /*
         * RngSeedLogger scrive in append.
         *
         * Per una nuova campagna cancelliamo
         * quindi il vecchio file, se esiste.
         */
        if (seedFile.exists()
                && !seedFile.delete()) {


            throw new IllegalStateException(
                    "Impossibile eliminare il vecchio file RNG: "
                            + seedFile.getAbsolutePath()
            );
        }

        try (FileWriter fw =
                     new FileWriter(outFile)) {


            fw.write(
                    "scenario,lambda,"
                            + "R_mean,R_hw,"
                            + "N_A_mean,N_A_hw,"
                            + "N_B_mean,N_B_hw,"
                            + "N_P_mean,N_P_hw,"
                            + "U_A_mean,U_A_hw,"
                            + "U_B_mean,U_B_hw,"
                            + "U_P_mean,U_P_hw,"
                            + "X_A_mean,X_A_hw,"
                            + "X_B_mean,X_B_hw,"
                            + "X_P_mean,X_P_hw\n"
            );


            /*
             * =================================================
             * CICLO SULLE CONFIGURAZIONI
             * =================================================
             */

            for (double lambda :
                    lambdas) {


                for (boolean is2FA :
                        scenarios2FA) {


                    String scenario =
                            is2FA
                                    ? "2FA"
                                    : "1FA";


                    System.out.printf(
                            Locale.US,

                            "%n========================================%n"
                                    + "Avvio simulazione: "
                                    + "%s, lambda=%.2f%n"
                                    + "========================================%n",

                            scenario,
                            lambda
                    );


                    /*
                     * =================================================
                     * 1. PARAMETRI DELLA CONFIGURAZIONE
                     * =================================================
                     */

                    Params params =
                            new Params();


                    params.lambda =
                            lambda;


                    params.is2FA_enabled =
                            is2FA;


                    /*
                     * =================================================
                     * 2. RNG
                     * =================================================
                     *
                     * Ogni NUOVA configurazione riparte
                     * dal medesimo MASTER_SEED.
                     *
                     * Questa campagna studia il modello
                     * nominale, quindi B è HyperExp.
                     */
                    RandomGenerator rng =
                            new RandomGenerator(
                                    MASTER_SEED,
                                    true
                            );


                    /*
                     * =================================================
                     * 3. LOG DELLO STATO INIZIALE DEGLI STREAM
                     * =================================================
                     * questo viene fatto PRIMA della
                     * creazione del SimulationEngine,
                     * perché il costruttore dell'engine
                     * può già consumare numeri casuali
                     * per inizializzare il prossimo arrivo.
                     */

                    String configuration =
                            String.format(
                                    Locale.US,
                                    "%s_lambda_%.2f",
                                    scenario,
                                    lambda
                            );


                    RngSeedLogger.append(
                            seedOutPath,
                            "RegimeExperiment",
                            configuration,
                            0,
                            rng
                    );


                    /*
                     * repetition = 0 perché per ogni
                     * configurazione del regime abbiamo
                     * una sola long run.
                     */


                    /*
                     * =================================================
                     * 4. SERVER
                     * =================================================
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


                    /*
                     * =================================================
                     * 5. SISTEMA
                     * =================================================
                     */

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


                    /*
                     * =================================================
                     * 6. WARM-UP
                     * =================================================
                     */

                    System.out.println(
                            "--- Warm-up ---"
                    );


                    engine.runForTime(
                            WARMUP_TIME
                    );


                    double measurementStart =
                            engine.getClock();


                    /*
                     * Eliminiamo le statistiche raccolte
                     * durante il transitorio.
                     *
                     * NON vengono resettati:
                     *
                     * - RNG
                     * - server
                     * - job presenti nel sistema
                     * - clock della simulazione
                     */
                    ctx.metrics.reset();


                    /*
                     * Per il response time globale R
                     * consideriamo soltanto job il cui
                     * arrivo esterno sia avvenuto dopo
                     * il termine del warm-up.
                     */
                    ctx.metrics
                            .setResponseCollectionStartTime(
                                    measurementStart
                            );


                    /*
                     * =================================================
                     * 7. BATCHING TEMPORALE
                     * =================================================
                     *
                     * La durata temporale del batch viene
                     * scelta in modo che mediamente vi siano
                     * BATCH_SIZE = 8000 arrivi esterni.
                     */
                    double deltaT =
                            BATCH_SIZE
                                    / lambda;


                    ctx.metrics
                            .enableTimeBatching(
                                    deltaT,
                                    measurementStart
                            );


                    /*
                     * =================================================
                     * 8. MISURA A REGIME
                     * =================================================
                     */

                    System.out.println(
                            "--- Misura a regime ---"
                    );


                    /*
                     * Primo obiettivo:
                     *
                     * 800000 completamenti post-reset.
                     *
                     * Alcuni di questi job potrebbero però
                     * essere arrivati PRIMA del termine
                     * del warm-up.
                     *
                     * Continuiamo quindi finché abbiamo:
                     *
                     * - almeno 800000 response time validi;
                     * - almeno 100 batch temporali.
                     */
                    long completionTarget =
                            jobsAfterWarmup;


                    while (true) {


                        engine.run(
                                completionTarget
                        );


                        int validResponseTimes =
                                ctx.metrics
                                        .getResponseTimesSystem()
                                        .size();


                        int timeBatches =
                                ctx.metrics
                                        .getTimeBatchCollector()
                                        .getBatchMeansNA()
                                        .size();


                        boolean enoughResponseTimes =
                                validResponseTimes
                                        >= jobsAfterWarmup;


                        boolean enoughTimeBatches =
                                timeBatches
                                        >= NUM_BATCHES;


                        if (enoughResponseTimes
                                && enoughTimeBatches) {


                            break;
                        }
                        completionTarget +=
                                BATCH_SIZE;
                    }


                    /*
                     * =================================================
                     * 9. RESPONSE TIME R
                     * =================================================
                     *
                     * Utilizziamo ESATTAMENTE:
                     *
                     * 100 batch × 8000 response time.
                     */

                    List<Double> validR =
                            firstN(
                                    ctx.metrics
                                            .getResponseTimesSystem(),

                                    (int) jobsAfterWarmup
                            );


                    BatchMeansAnalyzer
                            .ConfidenceInterval ciR =

                            BatchMeansAnalyzer
                                    .computeCI(

                                            BatchMeansAnalyzer
                                                    .batchMeansFromSequence(
                                                            validR,
                                                            BATCH_SIZE
                                                    )
                                    );


                    /*
                     * =================================================
                     * 10. STATISTICHE TEMPORALI
                     * =================================================
                     */

                    TimeBatchCollector tb =
                            ctx.metrics
                                    .getTimeBatchCollector();


                    /*
                     * N_A
                     */
                    BatchMeansAnalyzer
                            .ConfidenceInterval ciNA =

                            BatchMeansAnalyzer
                                    .computeCI(

                                            firstN(
                                                    tb.getBatchMeansNA(),
                                                    NUM_BATCHES
                                            )
                                    );


                    /*
                     * N_B
                     */
                    BatchMeansAnalyzer
                            .ConfidenceInterval ciNB =

                            BatchMeansAnalyzer
                                    .computeCI(

                                            firstN(
                                                    tb.getBatchMeansNB(),
                                                    NUM_BATCHES
                                            )
                                    );


                    /*
                     * N_P
                     */
                    BatchMeansAnalyzer
                            .ConfidenceInterval ciNP =

                            BatchMeansAnalyzer
                                    .computeCI(

                                            firstN(
                                                    tb.getBatchMeansNP(),
                                                    NUM_BATCHES
                                            )
                                    );


                    /*
                     * U_A
                     */
                    BatchMeansAnalyzer
                            .ConfidenceInterval ciUA =

                            BatchMeansAnalyzer
                                    .computeCI(

                                            firstN(
                                                    tb.getBatchMeansUA(),
                                                    NUM_BATCHES
                                            )
                                    );


                    /*
                     * U_B
                     */
                    BatchMeansAnalyzer
                            .ConfidenceInterval ciUB =

                            BatchMeansAnalyzer
                                    .computeCI(

                                            firstN(
                                                    tb.getBatchMeansUB(),
                                                    NUM_BATCHES
                                            )
                                    );


                    /*
                     * U_P
                     */
                    BatchMeansAnalyzer
                            .ConfidenceInterval ciUP =

                            BatchMeansAnalyzer
                                    .computeCI(

                                            firstN(
                                                    tb.getBatchMeansUP(),
                                                    NUM_BATCHES
                                            )
                                    );


                    /*
                     * X_A
                     */
                    BatchMeansAnalyzer
                            .ConfidenceInterval ciXA =

                            BatchMeansAnalyzer
                                    .computeCI(

                                            firstN(
                                                    tb.getBatchMeansXA(),
                                                    NUM_BATCHES
                                            )
                                    );


                    /*
                     * X_B
                     */
                    BatchMeansAnalyzer
                            .ConfidenceInterval ciXB =

                            BatchMeansAnalyzer
                                    .computeCI(

                                            firstN(
                                                    tb.getBatchMeansXB(),
                                                    NUM_BATCHES
                                            )
                                    );


                    /*
                     * X_P
                     */
                    BatchMeansAnalyzer
                            .ConfidenceInterval ciXP =

                            BatchMeansAnalyzer
                                    .computeCI(

                                            firstN(
                                                    tb.getBatchMeansXP(),
                                                    NUM_BATCHES
                                            )
                                    );


                    fw.write(
                            String.format(
                                    Locale.US,

                                    "%s,%.2f,"
                                            + "%.6f,%.6f,"
                                            + "%.6f,%.6f,"
                                            + "%.6f,%.6f,"
                                            + "%.6f,%.6f,"
                                            + "%.6f,%.6f,"
                                            + "%.6f,%.6f,"
                                            + "%.6f,%.6f,"
                                            + "%.6f,%.6f,"
                                            + "%.6f,%.6f,"
                                            + "%.6f,%.6f%n",

                                    scenario,
                                    lambda,

                                    ciR.mean,
                                    ciR.halfWidth,

                                    ciNA.mean,
                                    ciNA.halfWidth,

                                    ciNB.mean,
                                    ciNB.halfWidth,

                                    ciNP.mean,
                                    ciNP.halfWidth,

                                    ciUA.mean,
                                    ciUA.halfWidth,

                                    ciUB.mean,
                                    ciUB.halfWidth,

                                    ciUP.mean,
                                    ciUP.halfWidth,

                                    ciXA.mean,
                                    ciXA.halfWidth,

                                    ciXB.mean,
                                    ciXB.halfWidth,

                                    ciXP.mean,
                                    ciXP.halfWidth
                            )
                    );

                    System.out.printf(
                            Locale.US,

                            "Completamenti post-reset = %d%n"
                                    + "Response time validi     = %d%n"
                                    + "Batch temporali prodotti = %d%n"
                                    + "R   = %.6f +/- %.6f%n"
                                    + "N_B = %.6f +/- %.6f%n"
                                    + "U_B = %.6f +/- %.6f%n"
                                    + "X_B = %.6f +/- %.6f%n",

                            ctx.metrics
                                    .getTotalJobsCompleted(),

                            ctx.metrics
                                    .getResponseTimesSystem()
                                    .size(),

                            tb.getBatchMeansNA()
                                    .size(),

                            ciR.mean,
                            ciR.halfWidth,

                            ciNB.mean,
                            ciNB.halfWidth,

                            ciUB.mean,
                            ciUB.halfWidth,

                            ciXB.mean,
                            ciXB.halfWidth
                    );
                }
            }
        }

        System.out.println(
                "\nFile risultati:"
        );

        System.out.println(
                outFile.getAbsolutePath()
        );


        System.out.println(
                "\nFile seed RNG:"
        );

        System.out.println(
                seedFile.getAbsolutePath()
        );
    }


    /*
     * Restituisce esattamente le prime n osservazioni.
     *
     * In questo modo gli intervalli di confidenza
     * vengono sempre costruiti usando:
     *
     * R:
     * 100 × 8000 campioni
     *
     * N/U/X:
     * 100 batch temporali
     */

    private static List<Double> firstN(
            List<Double> values,
            int n) {


        if (values.size()
                < n) {


            throw new IllegalStateException(
                    "Osservazioni insufficienti: richieste "
                            + n
                            + ", disponibili "
                            + values.size()
            );
        }


        return new ArrayList<>(
                values.subList(
                        0,
                        n
                )
        );
    }
}