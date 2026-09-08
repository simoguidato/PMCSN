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
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Confronto tra:
 *
 * - Server B Esponenziale
 * - Server B HyperExp con CV = 2
 *
 * Punto operativo:
 *
 * lambda = 1.20
 * scenario = 1FA
 *
 * Protocollo:
 *
 * - warm-up = 100000 s
 * - 100 batch
 * - batch size = 8000
 * - intervalli di confidenza al 95%
 *
 * Le due distribuzioni rappresentano due configurazioni
 * sperimentali distinte.
 *
 * Di conseguenza entrambe vengono inizializzate dal
 * medesimo MASTER_SEED.
 */
public class ExpVsHyperExpComparison {

    private static final long MASTER_SEED =
            123456789L;

    private static final double LAMBDA =
            1.20;

    private static final double WARMUP_TIME =
            100_000.0;

    private static final int BATCH_SIZE =
            8000;

    private static final int NUM_BATCHES =
            100;


    public static void main(String[] args)
            throws Exception {


        /*
         * Numero di response time validi richiesti:
         *
         * 100 × 8000 = 800000
         */
        long requiredResponses =
                (long) BATCH_SIZE
                        * NUM_BATCHES;
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


        File summaryFile =
                new File(
                        csvDir,
                        "exp_vs_hyperexp_comparison.csv"
                );


        File seedsFile =
                new File(
                        csvDir,
                        "exp_vs_hyperexp_rng_seeds.csv"
                );


        /*
         * RngSeedLogger scrive in append.
         *
         * Per una nuova campagna cancelliamo
         * il vecchio file dei seed.
         */
        if (seedsFile.exists()
                && !seedsFile.delete()) {

            throw new IllegalStateException(
                    "Impossibile eliminare il vecchio file RNG: "
                            + seedsFile.getAbsolutePath()
            );
        }

        try (FileWriter summaryWriter =
                     new FileWriter(summaryFile)) {


            summaryWriter.write(
                    "distribution,"
                            + "R_mean,R_hw,"
                            + "N_B_mean,N_B_hw,"
                            + "U_B_mean,U_B_hw,"
                            + "X_B_mean,X_B_hw,"
                            + "B_mean,B_std,B_cv,"
                            + "B_p95,B_max,B_samples\n"
            );


            /*
             * false -> B Exp
             * true  -> B HyperExp CV=2
             */
            boolean[] distributions = {

                    false,
                    true
            };


            /*
             * =================================================
             * CICLO SULLE DUE CONFIGURAZIONI
             * =================================================
             */

            for (boolean hyperExp :
                    distributions) {


                String label =
                        hyperExp
                                ? "HyperExp"
                                : "Exp";


                System.out.printf(
                        Locale.US,

                        "%n========================================%n"
                                + "Server B: %s%n"
                                + "lambda = %.2f%n"
                                + "scenario = 1FA%n"
                                + "========================================%n",

                        label,
                        LAMBDA
                );


                /*
                 * =================================================
                 * 1. PARAMETRI
                 * =================================================
                 */

                Params params =
                        new Params();


                params.lambda =
                        LAMBDA;


                params.is2FA_enabled =
                        false;


                /*
                 * =================================================
                 * 2. RNG
                 * =================================================
                 *
                 * Exp e HyperExp sono due NUOVE
                 * configurazioni.
                 *
                 * Entrambe ripartono quindi dal
                 * medesimo MASTER_SEED.
                 */
                RandomGenerator rng =
                        new RandomGenerator(
                                MASTER_SEED,
                                hyperExp
                        );


                /*
                 * =================================================
                 * 3. LOG DEGLI STREAM RNG
                 * =================================================
                 *
                 * Il logging viene effettuato PRIMA
                 * della creazione del SimulationEngine,
                 * cioè prima che vengano consumati
                 * numeri casuali per inizializzare
                 * la simulazione.
                 *
                 * repetition = 0:
                 *
                 * ciascuna configurazione viene
                 * eseguita mediante una singola
                 * long run.
                 */
                RngSeedLogger.append(
                        seedsFile.getPath(),
                        "ExpVsHyperExpComparison",
                        label,
                        0,
                        rng
                );


                /*
                 * =================================================
                 * 4. SERVER PS
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
                 * Azzeriamo le statistiche raccolte
                 * durante il transitorio.
                 *
                 * NON vengono resettati:
                 *
                 * - RNG
                 * - stato dei server
                 * - job presenti
                 * - clock
                 */
                ctx.metrics.reset();


                /*
                 * Per R vengono considerati soltanto
                 * i job il cui arrivo esterno è
                 * successivo al warm-up.
                 */
                ctx.metrics
                        .setResponseCollectionStartTime(
                                measurementStart
                        );


                /*
                 * =================================================
                 * 7. BATCHING TEMPORALE
                 * =================================================
                 */

                double deltaT =
                        BATCH_SIZE
                                / LAMBDA;


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


                long completionTarget =
                        requiredResponses;


                /*
                 * Alcuni job completati dopo il reset
                 * potrebbero essere arrivati prima
                 * del termine del warm-up.
                 *
                 * Continuiamo quindi finché abbiamo:
                 *
                 * - almeno 800000 response time validi
                 * - almeno 100 batch temporali
                 */
                while (true) {


                    engine.run(
                            completionTarget
                    );


                    int validResponses =
                            ctx.metrics
                                    .getResponseTimesSystem()
                                    .size();


                    int timeBatches =
                            ctx.metrics
                                    .getTimeBatchCollector()
                                    .getBatchMeansNB()
                                    .size();


                    boolean enoughResponses =
                            validResponses
                                    >= requiredResponses;


                    boolean enoughTimeBatches =
                            timeBatches
                                    >= NUM_BATCHES;


                    if (enoughResponses
                            && enoughTimeBatches) {

                        break;
                    }


                    /*
                     * Prolunghiamo la simulazione
                     * di altri 8000 completamenti.
                     */
                    completionTarget +=
                            BATCH_SIZE;
                }


                /*
                 * =================================================
                 * 9. RESPONSE TIME END-TO-END
                 * =================================================
                 *
                 * Usiamo esattamente:
                 *
                 * 100 × 8000 response time.
                 */

                List<Double> validR =
                        firstN(
                                ctx.metrics
                                        .getResponseTimesSystem(),

                                (int) requiredResponses
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
                 * 10. STATISTICHE SERVER B
                 * =================================================
                 */

                TimeBatchCollector tb =
                        ctx.metrics
                                .getTimeBatchCollector();


                BatchMeansAnalyzer
                        .ConfidenceInterval ciNB =

                        BatchMeansAnalyzer
                                .computeCI(

                                        firstN(
                                                tb.getBatchMeansNB(),
                                                NUM_BATCHES
                                        )
                                );


                BatchMeansAnalyzer
                        .ConfidenceInterval ciUB =

                        BatchMeansAnalyzer
                                .computeCI(

                                        firstN(
                                                tb.getBatchMeansUB(),
                                                NUM_BATCHES
                                        )
                                );


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
                 * =================================================
                 * 11. DISTRIBUZIONE DEI TEMPI DI SOSTA A B
                 * =================================================
                 *
                 * Qui non studiamo soltanto la media,
                 * ma anche:
                 *
                 * - deviazione standard
                 * - CV
                 * - percentile 95
                 * - massimo
                 *
                 * Serve per confrontare la variabilità
                 * del caso Exp con HyperExp.
                 */

                List<Double> responseTimesB =
                        ctx.metrics
                                .getResponseTimesB();


                double meanB =
                        mean(
                                responseTimesB
                        );


                double stdB =
                        standardDeviation(
                                responseTimesB,
                                meanB
                        );


                double cvB =
                        stdB / meanB;


                double p95B =
                        percentile(
                                responseTimesB,
                                0.95
                        );


                double maxB =
                        responseTimesB
                                .stream()
                                .mapToDouble(
                                        Double::doubleValue
                                )
                                .max()
                                .orElse(
                                        Double.NaN
                                );


                /*
                 * =================================================
                 * 12. ESPORTAZIONE DEI RESPONSE TIME GREZZI DI B
                 * =================================================
                 */

                File rawFile =
                        new File(
                                csvDir,
                                "response_times_B_"
                                        + label
                                        + ".csv"
                        );


                try (FileWriter rawWriter =
                             new FileWriter(rawFile)) {


                    rawWriter.write(
                            "value\n"
                    );


                    for (double value :
                            responseTimesB) {


                        rawWriter.write(
                                String.format(
                                        Locale.US,
                                        "%.12f%n",
                                        value
                                )
                        );
                    }
                }


                /*
                 * =================================================
                 * 13. OUTPUT CONSOLE
                 * =================================================
                 */

                System.out.printf(
                        Locale.US,

                        "Completamenti post-reset = %d%n"
                                + "Response time validi     = %d%n"
                                + "Batch temporali prodotti = %d%n"
                                + "%n"
                                + "R   = %.6f +/- %.6f%n"
                                + "N_B = %.6f +/- %.6f%n"
                                + "U_B = %.6f +/- %.6f%n"
                                + "X_B = %.6f +/- %.6f%n"
                                + "%n"
                                + "Tempi individuali B:%n"
                                + "n    = %d%n"
                                + "mean = %.6f%n"
                                + "std  = %.6f%n"
                                + "CV   = %.6f%n"
                                + "p95  = %.6f%n"
                                + "max  = %.6f%n",

                        ctx.metrics
                                .getTotalJobsCompleted(),

                        ctx.metrics
                                .getResponseTimesSystem()
                                .size(),

                        tb.getBatchMeansNB()
                                .size(),

                        ciR.mean,
                        ciR.halfWidth,

                        ciNB.mean,
                        ciNB.halfWidth,

                        ciUB.mean,
                        ciUB.halfWidth,

                        ciXB.mean,
                        ciXB.halfWidth,

                        responseTimesB.size(),
                        meanB,
                        stdB,
                        cvB,
                        p95B,
                        maxB
                );


                summaryWriter.write(
                        String.format(
                                Locale.US,

                                "%s,"
                                        + "%.6f,%.6f,"
                                        + "%.6f,%.6f,"
                                        + "%.6f,%.6f,"
                                        + "%.6f,%.6f,"
                                        + "%.6f,%.6f,%.6f,"
                                        + "%.6f,%.6f,%d%n",

                                label,

                                ciR.mean,
                                ciR.halfWidth,

                                ciNB.mean,
                                ciNB.halfWidth,

                                ciUB.mean,
                                ciUB.halfWidth,

                                ciXB.mean,
                                ciXB.halfWidth,

                                meanB,
                                stdB,
                                cvB,
                                p95B,
                                maxB,

                                responseTimesB.size()
                        )
                );
            }
        }

        System.out.println(
                "\nFile risultati:"
        );

        System.out.println(
                summaryFile.getAbsolutePath()
        );


        System.out.println(
                "\nFile seed RNG:"
        );

        System.out.println(
                seedsFile.getAbsolutePath()
        );


        System.out.println(
                "\nFile response time B:"
        );

        System.out.println(
                new File(
                        csvDir,
                        "response_times_B_Exp.csv"
                ).getAbsolutePath()
        );

        System.out.println(
                new File(
                        csvDir,
                        "response_times_B_HyperExp.csv"
                ).getAbsolutePath()
        );
    }

    private static List<Double> firstN(
            List<Double> values,
            int n) {


        if (values.size()
                < n) {


            throw new IllegalStateException(
                    "Campioni insufficienti: richiesti "
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


    private static double mean(
            List<Double> values) {


        if (values.isEmpty()) {

            return Double.NaN;
        }


        double sum =
                0.0;


        for (double value :
                values) {

            sum +=
                    value;
        }


        return sum
                / values.size();
    }


    private static double standardDeviation(
            List<Double> values,
            double mean) {


        if (values.isEmpty()) {

            return Double.NaN;
        }


        double sum =
                0.0;


        for (double value :
                values) {


            double difference =
                    value
                            - mean;


            sum +=
                    difference
                            * difference;
        }


        /*
         * Qui si descrive empiricamente
         * l'intera sequenza osservata, quindi
         * utilizziamo 1/n.
         */
        return Math.sqrt(
                sum
                        / values.size()
        );
    }


    private static double percentile(
            List<Double> data,
            double p) {


        if (data.isEmpty()) {

            return Double.NaN;
        }


        List<Double> sorted =
                new ArrayList<>(
                        data
                );


        Collections.sort(
                sorted
        );


        int index =
                (int) Math.ceil(
                        p
                                * sorted.size()
                )
                        - 1;


        index =
                Math.max(
                        0,
                        Math.min(
                                index,
                                sorted.size() - 1
                        )
                );


        return sorted.get(
                index
        );
    }
}