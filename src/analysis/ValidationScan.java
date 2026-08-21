package analysis;

import controllers.PSServer;
import engine.SimulationEngine;
import engine.SystemContext;
import model.ServerState;
import utils.Params;
import utils.RandomGenerator;

import java.io.FileWriter;
import java.util.Locale;

public class ValidationScan {

    public static void main(String[] args) throws Exception {

        long seed = 123456789L;      // stesso seed per tutti i punti: le differenze sono solo dovute a lambda/scenario
        long jobsPerRun = 200_000;

        double[] lambdas = new double[15];
        for (int i = 0; i < 15; i++) lambdas[i] = 0.5 + i * 0.05;

        String outPath = "validation_scan.csv";
        try (FileWriter fw = new FileWriter(outPath)) {
            fw.write("scenario,lambda,R,N_A,N_B,N_P,N_tot,U_A,U_B,U_P,X_A,X_B,X_P\n");

            for (boolean is2FA : new boolean[]{false, true}) {
                String scenario = is2FA ? "2FA" : "1FA";

                for (double lambda : lambdas) {
                    Params params = new Params();
                    params.lambda = lambda;
                    params.is2FA_enabled = is2FA;

                    RandomGenerator rng = new RandomGenerator(seed);
                    PSServer serverA = new PSServer(1.0, ServerState.IDLE, 0);
                    PSServer serverB = new PSServer(1.0, ServerState.IDLE, 1);
                    PSServer serverP = new PSServer(1.0, ServerState.IDLE, 2);

                    SystemContext ctx = new SystemContext(params, rng, serverA, serverB, serverP);
                    SimulationEngine engine = new SimulationEngine(ctx);
                    engine.run(jobsPerRun);

                    double clock = engine.getClock();
                    double R = ctx.metrics.getAverageResponseTime();
                    double NA = ctx.metrics.getAverageJobsInSystemA(clock);
                    double NB = ctx.metrics.getAverageJobsInSystemB(clock);
                    double NP = ctx.metrics.getAverageJobsInSystemP(clock);
                    double UA = ctx.metrics.getUtilizationA(clock);
                    double UB = ctx.metrics.getUtilizationB(clock);
                    double UP = ctx.metrics.getUtilizationP(clock);
                    double XA = ctx.metrics.getThroughputA(clock);
                    double XB = ctx.metrics.getThroughputB(clock);
                    double XP = ctx.metrics.getThroughputP(clock);

                    fw.write(String.format(Locale.US,
                            "%s,%.2f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                            scenario, lambda, R, NA, NB, NP, (NA + NB + NP), UA, UB, UP, XA, XB, XP));

                    System.out.printf("%s  lambda=%.2f  ->  R=%.4f  N_tot=%.4f%n",
                            scenario, lambda, R, (NA + NB + NP));
                }
            }
        }

        System.out.println("\nFile esportato: " + new java.io.File(outPath).getAbsolutePath());
    }
}