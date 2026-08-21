package analysis.infinite_horizon;

import controllers.PSServer;
import engine.SimulationEngine;
import engine.SystemContext;
import metrics.BatchMeansAnalyzer;
import metrics.TimeBatchCollector;
import model.ServerState;
import utils.Params;
import utils.RandomGenerator;

import java.io.FileWriter;
import java.util.Locale;

/**
 * Simulazione a regime (orizzonte infinito) per gli Obiettivi 1 e 2:
 * scan di lambda 0.5-1.2 (step 0.05), scenari 1FA e 2FA, con scarto del transitorio
 * (soglia determinata in Sez. "Analisi del transitorio") e stima di R, N, U, X
 * tramite batch means con intervallo di confidenza al 95%.
 */
public class RegimeExperiment {

    public static void main(String[] args) throws Exception {

        long seed = 123456789L;
        double warmupTime = 100_000;   // soglia di warm-up (Analisi del transitorio)
        int batchSize = 8000;          // batch size (Autocorrelazione)
        int targetBatches = 100;       // minimo di batch per un IC affidabile
        long jobsAfterWarmup = (long) batchSize * targetBatches;

        double[] lambdas = new double[15];
        for (int i = 0; i < 15; i++) lambdas[i] = 0.5 + i * 0.05;

        String outPath = "regime_experiment.csv";
        try (FileWriter fw = new FileWriter(outPath)) {
            fw.write("scenario,lambda,"
                    + "R_mean,R_hw,R_batches,"
                    + "N_A_mean,N_A_hw,N_B_mean,N_B_hw,N_P_mean,N_P_hw,"
                    + "U_A_mean,U_A_hw,U_B_mean,U_B_hw,U_P_mean,U_P_hw,"
                    + "X_A_mean,X_A_hw,X_B_mean,X_B_hw,X_P_mean,X_P_hw\n");

            for (boolean is2FA : new boolean[]{false, true}) {
                String scenario = is2FA ? "2FA" : "1FA";

                for (double lambda : lambdas) {
                    System.out.printf("%n=== %s, lambda=%.2f ===%n", scenario, lambda);

                    Params params = new Params();
                    params.lambda = lambda;
                    params.is2FA_enabled = is2FA;

                    RandomGenerator rng = new RandomGenerator(seed);
                    PSServer serverA = new PSServer(1.0, ServerState.IDLE, 0);
                    PSServer serverB = new PSServer(1.0, ServerState.IDLE, 1);
                    PSServer serverP = new PSServer(1.0, ServerState.IDLE, 2);

                    SystemContext ctx = new SystemContext(params, rng, serverA, serverB, serverP);
                    SimulationEngine engine = new SimulationEngine(ctx);

                    // --- Fase 1: scarto del transitorio ---
                    engine.runForTime(warmupTime);

                    // --- Fase 2: azzera i contatori "sporcati" dal warm-up ---
                    ctx.metrics.reset();
                    double deltaT = batchSize / lambda;
                    ctx.metrics.enableTimeBatching(deltaT, engine.getClock());

                    // --- Fase 3: raccolta vera e propria ---
                    engine.run(jobsAfterWarmup);

                    // --- Calcolo IC ---
                    BatchMeansAnalyzer.ConfidenceInterval ciR = BatchMeansAnalyzer.computeCI(
                            BatchMeansAnalyzer.batchMeansFromSequence(ctx.metrics.getResponseTimesSystem(), batchSize));

                    TimeBatchCollector tb = ctx.metrics.getTimeBatchCollector();
                    BatchMeansAnalyzer.ConfidenceInterval ciNA = BatchMeansAnalyzer.computeCI(tb.getBatchMeansNA());
                    BatchMeansAnalyzer.ConfidenceInterval ciNB = BatchMeansAnalyzer.computeCI(tb.getBatchMeansNB());
                    BatchMeansAnalyzer.ConfidenceInterval ciNP = BatchMeansAnalyzer.computeCI(tb.getBatchMeansNP());
                    BatchMeansAnalyzer.ConfidenceInterval ciUA = BatchMeansAnalyzer.computeCI(tb.getBatchMeansUA());
                    BatchMeansAnalyzer.ConfidenceInterval ciUB = BatchMeansAnalyzer.computeCI(tb.getBatchMeansUB());
                    BatchMeansAnalyzer.ConfidenceInterval ciUP = BatchMeansAnalyzer.computeCI(tb.getBatchMeansUP());
                    BatchMeansAnalyzer.ConfidenceInterval ciXA = BatchMeansAnalyzer.computeCI(tb.getBatchMeansXA());
                    BatchMeansAnalyzer.ConfidenceInterval ciXB = BatchMeansAnalyzer.computeCI(tb.getBatchMeansXB());
                    BatchMeansAnalyzer.ConfidenceInterval ciXP = BatchMeansAnalyzer.computeCI(tb.getBatchMeansXP());

                    StringBuilder sb = new StringBuilder();
                    sb.append(scenario).append(',').append(fmt(lambda)).append(',');
                    sb.append(fmt(ciR.mean)).append(',').append(fmt(ciR.halfWidth)).append(',').append(ciR.numBatches).append(',');
                    for (BatchMeansAnalyzer.ConfidenceInterval ci :
                            new BatchMeansAnalyzer.ConfidenceInterval[]{ciNA, ciNB, ciNP, ciUA, ciUB, ciUP, ciXA, ciXB, ciXP}) {
                        sb.append(fmt(ci.mean)).append(',').append(fmt(ci.halfWidth)).append(',');
                    }
                    sb.setLength(sb.length() - 1); // rimuove l'ultima virgola
                    sb.append('\n');
                    fw.write(sb.toString());

                    System.out.printf("R = %s%n", ciR);
                }
            }
        }

        System.out.println("\nFile esportato: " + new java.io.File(outPath).getAbsolutePath());
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.6f", v);
    }
}