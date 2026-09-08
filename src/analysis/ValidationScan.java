package analysis;

import controllers.PSServer;
import engine.SimulationEngine;
import engine.SystemContext;
import model.ServerState;
import utils.Params;
import utils.RandomGenerator;

import java.io.File;
import java.io.FileWriter;
import java.util.Locale;

public class ValidationScan {

    private static final long MASTER_SEED =
            123456789L;

    private static final long JOBS_PER_RUN =
            200_000;


    public static void main(String[] args)
            throws Exception {


        /*
         * 15 valori:
         *
         * 0.50, 0.55, ..., 1.20
         */
        double[] lambdas =
                new double[15];


        for (int i = 0;
             i < lambdas.length;
             i++) {

            lambdas[i] =
                    0.50 + i * 0.05;
        }

        String outPath =
                "csv/validation_scan.csv";

        File outFile =
                new File(outPath);



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


        try (FileWriter fw =
                     new FileWriter(outFile)) {


            /*
             * Header CSV.
             */
            fw.write(
                    "scenario,lambda,"
                            + "R,"
                            + "N_A,N_B,N_P,N_tot,"
                            + "U_A,U_B,U_P,"
                            + "X_A,X_B,X_P\n"
            );


            /*
             * Due scenari:
             *
             * false -> 1FA
             * true  -> 2FA
             */
            for (boolean is2FA :
                    new boolean[]{false, true}) {


                String scenario =
                        is2FA
                                ? "2FA"
                                : "1FA";


                /*
                 * Scan del tasso di arrivo.
                 */
                for (double lambda :
                        lambdas) {


                    Params params =
                            new Params();


                    params.lambda =
                            lambda;


                    params.is2FA_enabled =
                            is2FA;


                    /*
                     * Ogni punto dello scan
                     * costituisce una nuova
                     * configurazione sperimentale.
                     *
                     * Perciò ripartiamo dal
                     * medesimo master seed.
                     *
                     * false:
                     * Server B ESPONENZIALE
                     */
                    RandomGenerator rng =
                            new RandomGenerator(
                                    MASTER_SEED,
                                    false
                            );


                    /*
                     * Creazione dei tre server PS.
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
                     * Contesto della simulazione.
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

                    engine.run(
                            JOBS_PER_RUN
                    );


                    double clock =
                            engine.getClock();


                    /*
                     * ===========================
                     * TEMPO DI RISPOSTA
                     * ===========================
                     */

                    double R =
                            ctx.metrics
                                    .getAverageResponseTime();


                    /*
                     * ===========================
                     * POPOLAZIONE MEDIA
                     * ===========================
                     */

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


                    double Ntot =
                            NA + NB + NP;


                    /*
                     * ===========================
                     * UTILIZZAZIONI
                     * ===========================
                     */

                    double UA =
                            ctx.metrics
                                    .getUtilizationA(
                                            clock
                                    );


                    double UB =
                            ctx.metrics
                                    .getUtilizationB(
                                            clock
                                    );


                    double UP =
                            ctx.metrics
                                    .getUtilizationP(
                                            clock
                                    );


                    /*
                     * ===========================
                     * THROUGHPUT
                     * ===========================
                     */

                    double XA =
                            ctx.metrics
                                    .getThroughputA(
                                            clock
                                    );


                    double XB =
                            ctx.metrics
                                    .getThroughputB(
                                            clock
                                    );


                    double XP =
                            ctx.metrics
                                    .getThroughputP(
                                            clock
                                    );

                    fw.write(
                            String.format(
                                    Locale.US,

                                    "%s,%.2f,"
                                            + "%.6f,"
                                            + "%.6f,%.6f,%.6f,%.6f,"
                                            + "%.6f,%.6f,%.6f,"
                                            + "%.6f,%.6f,%.6f%n",

                                    scenario,
                                    lambda,

                                    R,

                                    NA,
                                    NB,
                                    NP,
                                    Ntot,

                                    UA,
                                    UB,
                                    UP,

                                    XA,
                                    XB,
                                    XP
                            )
                    );

                    System.out.printf(
                            Locale.US,

                            "%s  lambda=%.2f"
                                    + " -> R=%.4f"
                                    + " N_tot=%.4f%n",

                            scenario,
                            lambda,
                            R,
                            Ntot
                    );
                }
            }
        }

        System.out.println(
                "\nFile esportato: "
                        + outFile.getAbsolutePath()
        );
    }
}