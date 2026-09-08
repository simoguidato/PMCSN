package analysis;

import controllers.PSServer;
import engine.SimulationEngine;
import engine.SystemContext;
import model.ServerState;
import utils.Params;
import utils.RandomGenerator;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Locale;

public class AutocorrelationAnalysis {

    private static final long MASTER_SEED =
            123456789L;

    /*
     * Punto operativo più critico ma ancora stabile.
     */
    private static final double LAMBDA =
            1.20;

    /*
     * Warm-up determinato mediante
     * l'analisi del transitorio.
     */
    private static final double WARMUP_TIME =
            100_000.0;

    /*
     * Numero di job completati che osserviamo
     * DOPO il warm-up.
     *
     * Non è una batch size.
     * Serve solo ad avere una sequenza abbastanza
     * lunga per stimare l'ACF.
     */
    private static final long JOBS_AFTER_WARMUP =
            800_000L;


    public static void main(String[] args)
            throws Exception {


        System.out.println(
                "==========================================="
        );

        System.out.println(
                "Analisi autocorrelazione - Server B"
        );

        System.out.println(
                "lambda = " + LAMBDA
        );

        System.out.println(
                "scenario = 1FA"
        );

        System.out.println(
                "Server B = HyperExp, CV = 2"
        );

        System.out.println(
                "warm-up = " + WARMUP_TIME + " s"
        );

        System.out.println(
                "==========================================="
        );


        /*
         * ============================
         * PARAMETRI DEL MODELLO
         * ============================
         */

        Params params =
                new Params();

        params.lambda =
                LAMBDA;

        params.is2FA_enabled =
                false;


        /*
         * true:
         * Server B usa HyperExp con CV=2.
         */
        RandomGenerator rng =
                new RandomGenerator(
                        MASTER_SEED,
                        true
                );


        /*
         * ============================
         * SERVER PROCESSOR SHARING
         * ============================
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
         * ============================
         * SISTEMA
         * ============================
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
         * ============================
         * 1. WARM-UP
         * ============================
         */

        System.out.println(
                "\n--- Warm-up ---"
        );


        engine.runForTime(
                WARMUP_TIME
        );


        System.out.printf(
                Locale.US,
                "Warm-up terminato a t = %.3f s%n",
                engine.getClock()
        );


        /*
         * Salviamo gli stati degli stream
         * nel momento esatto in cui inizierà
         * la raccolta dei dati.
         */
        long[] streamSeeds =
                rng.snapshotUsedStreamSeeds();

        ctx.metrics.reset();

        /*
         * ============================
         * 2. RACCOLTA A REGIME
         * ============================
         *
         * Non abilitiamo alcun batching.
         *
         * Vogliamo la sequenza grezza:
         *
         * T_B(1), T_B(2), ..., T_B(n)
         *
         * dei tempi individuali di permanenza
         * al Server B.
         */

        System.out.println(
                "\n--- Raccolta osservazioni a regime ---"
        );


        engine.run(
                JOBS_AFTER_WARMUP
        );


        List<Double> responseTimesB =
                ctx.metrics.getResponseTimesB();


        System.out.printf(
                Locale.US,
                "Fine raccolta a t = %.3f s%n",
                engine.getClock()
        );


        System.out.println(
                "Job completati post warm-up = "
                        + ctx.metrics
                        .getTotalJobsCompleted()
        );


        System.out.println(
                "Osservazioni raccolte su B = "
                        + responseTimesB.size()
        );


        /*
         * ============================
         * 3. STATISTICHE DESCRITTIVE
         * ============================
         */

        double mean =
                responseTimesB
                        .stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(Double.NaN);


        double variance =
                0.0;


        for (double x :
                responseTimesB) {

            double d =
                    x - mean;

            variance +=
                    d * d;
        }


        if (!responseTimesB.isEmpty()) {

            variance /=
                    responseTimesB.size();
        }


        double std =
                Math.sqrt(
                        variance
                );


        System.out.printf(
                Locale.US,
                "%nTempo medio B = %.6f s%n",
                mean
        );


        System.out.printf(
                Locale.US,
                "Deviazione standard B = %.6f s%n",
                std
        );


        System.out.printf(
                Locale.US,
                "CV response time B = %.6f%n",
                std / mean
        );


        /*
         * ============================
         * 4. DIRECTORY OUTPUT
         * ============================
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
         * ============================
         * 5. SEQUENZA PER ACF
         * ============================
         */

        File responseFile =
                new File(
                        csvDir,
                        "response_times_B_HyperExp_acf.csv"
                );


        try (FileWriter fw =
                     new FileWriter(responseFile)) {


            fw.write(
                    "index,response_time_B\n"
            );


            for (int i = 0;
                 i < responseTimesB.size();
                 i++) {


                fw.write(
                        String.format(
                                Locale.US,
                                "%d,%.12f%n",
                                i + 1,
                                responseTimesB.get(i)
                        )
                );
            }
        }


        /*
         * ============================
         * 6. STATO RNG ALL'INIZIO
         *    DELLA FASE DI MISURA
         * ============================
         */

        File seedFile =
                new File(
                        csvDir,
                        "autocorrelation_rng_seeds.csv"
                );


        try (FileWriter fw =
                     new FileWriter(seedFile)) {


            fw.write(
                    "stream,seed_at_measurement_start\n"
            );


            for (int i = 0;
                 i < streamSeeds.length;
                 i++) {


                fw.write(
                        String.format(
                                Locale.US,
                                "%d,%d%n",
                                i,
                                streamSeeds[i]
                        )
                );
            }
        }

        System.out.println(
                "\nFile risposta B:"
        );

        System.out.println(
                responseFile.getAbsolutePath()
        );


        System.out.println(
                "\nFile stato RNG:"
        );

        System.out.println(
                seedFile.getAbsolutePath()
        );


        System.out.println(
                "\nANALISI COMPLETATA."
        );
    }
}
