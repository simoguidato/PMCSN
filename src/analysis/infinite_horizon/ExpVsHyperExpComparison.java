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
import java.util.List;
import java.util.Locale;

/**
 * Confronto Server B Esponenziale vs Iperesponenziale al punto operativo piu' critico
 * (lambda=1.2, 1FA), stesso protocollo della Simulazione a regime (warm-up 100.000s,
 * batch means b=8000, 100 batch). Verifica empirica della proprieta' di insensibilita'
 * (medie attese uguali) e confronto della variabilita' dei tempi di risposta individuali.
 */
public class ExpVsHyperExpComparison {

    public static void main(String[] args) throws Exception {

        long seed = 123456789L;
        double lambda = 1.2;
        double warmupTime = 100_000;
        int batchSize = 8000;
        long jobsAfterWarmup = (long) batchSize * 100;

        for (boolean hyperExp : new boolean[]{false, true}) {
            String label = hyperExp ? "HyperExp" : "Exp";

            Params params = new Params();
            params.lambda = lambda;
            params.is2FA_enabled = false;

            RandomGenerator rng = new RandomGenerator(seed, hyperExp);
            PSServer serverA = new PSServer(1.0, ServerState.IDLE, 0);
            PSServer serverB = new PSServer(1.0, ServerState.IDLE, 1);
            PSServer serverP = new PSServer(1.0, ServerState.IDLE, 2);

            SystemContext ctx = new SystemContext(params, rng, serverA, serverB, serverP);
            SimulationEngine engine = new SimulationEngine(ctx);

            engine.runForTime(warmupTime);
            ctx.metrics.reset();
            ctx.metrics.enableTimeBatching(batchSize / lambda, engine.getClock());
            engine.run(jobsAfterWarmup);

            BatchMeansAnalyzer.ConfidenceInterval ciR = BatchMeansAnalyzer.computeCI(
                    BatchMeansAnalyzer.batchMeansFromSequence(ctx.metrics.getResponseTimesSystem(), batchSize));
            TimeBatchCollector tb = ctx.metrics.getTimeBatchCollector();
            BatchMeansAnalyzer.ConfidenceInterval ciNB = BatchMeansAnalyzer.computeCI(tb.getBatchMeansNB());
            BatchMeansAnalyzer.ConfidenceInterval ciUB = BatchMeansAnalyzer.computeCI(tb.getBatchMeansUB());
            BatchMeansAnalyzer.ConfidenceInterval ciXB = BatchMeansAnalyzer.computeCI(tb.getBatchMeansXB());

            System.out.printf("%n=== Server B: %s ===%n", label);
            System.out.printf("R    = %s%n", ciR);
            System.out.printf("N_B  = %s%n", ciNB);
            System.out.printf("U_B  = %s%n", ciUB);
            System.out.printf("X_B  = %s%n", ciXB);

            // Statistiche sulla distribuzione dei tempi di sosta individuali al Server B
            // (non solo le medie di batch, ma la variabilita' vera e propria)
            List<Double> rb = ctx.metrics.getResponseTimesB();
            double mean = rb.stream().mapToDouble(x -> x).average().orElse(0);
            double var = rb.stream().mapToDouble(x -> (x - mean) * (x - mean)).average().orElse(0);
            double std = Math.sqrt(var);
            double cv = std / mean;
            double p95 = percentile(rb, 0.95);
            double max = rb.stream().mapToDouble(x -> x).max().orElse(0);

            System.out.printf("Tempi di sosta a B (individuali, n=%d): media=%.3f  std=%.3f  CV=%.3f  p95=%.3f  max=%.3f%n",
                    rb.size(), mean, std, cv, p95, max);

            // Esporta la sequenza grezza per l'istogramma
            try (FileWriter fw = new FileWriter("response_times_B_" + label + ".csv")) {
                fw.write("value\n");
                for (double v : rb) fw.write(String.format(Locale.US, "%.6f%n", v));
            }
        }
    }

    private static double percentile(List<Double> data, double p) {
        List<Double> sorted = new java.util.ArrayList<>(data);
        java.util.Collections.sort(sorted);
        int idx = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }
}
