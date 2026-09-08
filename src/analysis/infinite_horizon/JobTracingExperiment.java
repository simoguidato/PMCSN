package analysis.infinite_horizon;

import controllers.PSServer;
import engine.SimulationEngine;
import engine.SystemContext;
import model.ServerState;
import utils.Params;
import utils.RandomGenerator;
import utils.RngSeedLogger;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Locale;

/**
 * Esperimento dedicato al tracing delle visite dei job.
 *
 * Configurazione:
 *
 * - scenario: 1FA
 * - lambda = 1.20 req/s
 * - Server B HyperExp, CV = 2
 * - warm-up = 100000 s
 *
 * Il tracing viene attivato soltanto DOPO il warm-up.
 *
 * Questa classe non sostituisce RegimeExperiment:
 * il suo obiettivo è esclusivamente produrre
 * informazioni per-visita sul workflow
 *
 * A1 -> B -> A2 -> P -> A3.
 */
public class JobTracingExperiment {

    private static final long MASTER_SEED =
            123456789L;

    private static final double LAMBDA =
            1.20;

    private static final double WARMUP_TIME =
            100_000.0;

    /*
     * Manteniamo lo stesso volume della campagna
     * stazionaria definitiva:
     *
     * 100 × 8000 = 800000 job validi.
     *
     * Il tracing non viene usato per ricalcolare
     * gli IC del regime: serve per la decomposizione
     * e la visualizzazione delle visite.
     */
    private static final long TARGET_VALID_JOBS =
            800_000L;

    /*
     * Se dopo i primi 800000 completamenti
     * alcuni job non risultano validi perché
     * appartenevano al warm-up, estendiamo
     * la simulazione a blocchi.
     */
    private static final int EXTENSION_BLOCK =
            8000;


    public static void main(String[] args)
            throws Exception {


        /*
         * =====================================================
         * DIRECTORY / FILE
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


        File traceFile =
                new File(
                        csvDir,
                        "trace_visite_job.csv"
                );


        File seedFile =
                new File(
                        csvDir,
                        "job_tracing_rng_seeds.csv"
                );


        File metadataFile =
                new File(
                        csvDir,
                        "job_tracing_metadata.csv"
                );


        /*
         * Rimuoviamo eventuali output precedenti.
         */
        deleteIfExists(
                traceFile
        );

        deleteIfExists(
                seedFile
        );

        deleteIfExists(
                metadataFile
        );


        /*
         * =====================================================
         * PARAMETRI
         * =====================================================
         */

        Params params =
                new Params();


        params.lambda =
                LAMBDA;


        params.is2FA_enabled =
                false;


        /*
         * =====================================================
         * RNG
         * =====================================================
         * Server B HyperExp CV=2.
         */
        RandomGenerator rng =
                new RandomGenerator(
                        MASTER_SEED,
                        true
                );


        /*
         * Stato iniziale degli stream.
         *
         * Deve essere registrato PRIMA della
         * creazione del SimulationEngine.
         */
        RngSeedLogger.append(
                seedFile.getPath(),
                "JobTracingExperiment",
                "1FA_lambda_1.20_HyperExp",
                0,
                rng
        );


        /*
         * =====================================================
         * SERVER
         * =====================================================
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
         * =====================================================
         * SISTEMA
         * =====================================================
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
         * =====================================================
         * WARM-UP
         * =====================================================
         */

        System.out.println(
                "========================================"
        );

        System.out.println(
                "JOB TRACING EXPERIMENT"
        );

        System.out.println(
                "1FA - lambda=1.20 - B HyperExp"
        );

        System.out.println(
                "========================================"
        );


        System.out.println(
                "--- Warm-up ---"
        );


        engine.runForTime(
                WARMUP_TIME
        );


        double measurementStart =
                engine.getClock();


        System.out.printf(
                Locale.US,
                "Fine warm-up: t = %.6f s%n",
                measurementStart
        );


        /*
         * =====================================================
         * RESET STATISTICHE
         * =====================================================
         * resettiamo le statistiche ma NON:
         *
         * - RNG
         * - clock
         * - server
         * - job presenti nel sistema
         */
        ctx.metrics.reset();


        /*
         * Escludiamo dalle statistiche R i job
         * arrivati prima della fine del warm-up.
         */
        ctx.metrics
                .setResponseCollectionStartTime(
                        measurementStart
                );


        /*
         * =====================================================
         * TRACING
         * =====================================================
         *
         * Il tracing viene abilitato SOLO ORA,
         * quindi non viene scritto il transitorio
         * iniziale nel file definitivo.
         */
        ctx.metrics.enableJobTracing(
                traceFile.getPath()
        );


        System.out.println(
                "--- Tracing a regime ---"
        );


        long completionTarget =
                TARGET_VALID_JOBS;


        try {


            while (true) {


                engine.run(
                        completionTarget
                );


                int validResponses =
                        ctx.metrics
                                .getResponseTimesSystem()
                                .size();


                System.out.printf(
                        Locale.US,

                        "Completamenti post-reset = %d, "
                                + "job validi = %d%n",

                        ctx.metrics
                                .getTotalJobsCompleted(),

                        validResponses
                );


                if (validResponses
                        >= TARGET_VALID_JOBS) {

                    break;
                }


                /*
                 * Sono rimasti nel sistema alcuni job
                 * arrivati prima della fine del warm-up.
                 *
                 * Continuiamo senza alcun reseed.
                 */
                completionTarget +=
                        EXTENSION_BLOCK;
            }


        } finally {


            /*
             * Chiusura garantita del file di tracing
             * anche in caso di eccezione.
             */
            ctx.metrics.closeTracing();
        }
        List<Double> validR =
                ctx.metrics
                        .getResponseTimesSystem();


        double meanR =
                meanFirstN(
                        validR,
                        (int) TARGET_VALID_JOBS
                );


        /*
         * =====================================================
         * METADATA
         * =====================================================
         *
         * Questo file ci permette di documentare
         * esattamente la configurazione utilizzata.
         */

        try (FileWriter fw =
                     new FileWriter(
                             metadataFile
                     )) {


            fw.write(
                    "scenario,"
                            + "lambda,"
                            + "distribution_B,"
                            + "warmup_time,"
                            + "measurement_start,"
                            + "target_valid_jobs,"
                            + "completed_post_reset,"
                            + "valid_response_samples,"
                            + "R_mean\n"
            );


            fw.write(
                    String.format(
                            Locale.US,

                            "1FA,"
                                    + "%.2f,"
                                    + "HyperExp_CV2,"
                                    + "%.1f,"
                                    + "%.6f,"
                                    + "%d,"
                                    + "%d,"
                                    + "%d,"
                                    + "%.6f%n",

                            LAMBDA,
                            WARMUP_TIME,
                            measurementStart,
                            TARGET_VALID_JOBS,

                            ctx.metrics
                                    .getTotalJobsCompleted(),

                            validR.size(),

                            meanR
                    )
            );
        }


        System.out.println(
                "\nJob tracing completato."
        );


        System.out.printf(
                Locale.US,
                "R medio sui primi %d job validi = %.6f s%n",
                TARGET_VALID_JOBS,
                meanR
        );


        System.out.println(
                "\nTrace:"
        );

        System.out.println(
                traceFile.getAbsolutePath()
        );


        System.out.println(
                "\nSeed RNG:"
        );

        System.out.println(
                seedFile.getAbsolutePath()
        );


        System.out.println(
                "\nMetadata:"
        );

        System.out.println(
                metadataFile.getAbsolutePath()
        );
    }


    private static double meanFirstN(
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


        double sum =
                0.0;


        for (int i = 0;
             i < n;
             i++) {

            sum +=
                    values.get(i);
        }


        return sum
                / n;
    }


    private static void deleteIfExists(
            File file) {


        if (file.exists()
                && !file.delete()) {

            throw new IllegalStateException(
                    "Impossibile eliminare il vecchio file: "
                            + file.getAbsolutePath()
            );
        }
    }
}
