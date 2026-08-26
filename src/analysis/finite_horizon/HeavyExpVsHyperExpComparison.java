package analysis.finite_horizon;

import controllers.PSServer;
import engine.SimulationEngine;
import engine.SystemContext;
import metrics.BatchMeansAnalyzer;
import metrics.TransientSampler;
import model.ServerState;
import utils.Params;
import utils.RandomGenerator;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Confronto Server B Esponenziale vs Iperesponenziale sullo scenario heavy (lambda=1.4),
 * stesso protocollo di HeavyLoadExperiment (30 repliche indipendenti, orizzonte 150.000s).
 * Verifica se il bias di throughput sotto overload (osservato con l'iperesponenziale)
 * si riduce con un servizio a varianza piu' bassa (esponenziale, CV=1).
 */
public class HeavyExpVsHyperExpComparison {

    public static void main(String[] args) throws Exception {

        double lambda = 1.4;
        double horizon = 150_000;
        double sampleInterval = 100;
        int numReplications = 30;

        int numSamples = (int) Math.floor(horizon / sampleInterval);

        long masterSeed = 123456789L; // Seed fisso

        for (boolean hyperExp : new boolean[]{false, true}) {
            String label = hyperExp ? "HyperExp" : "Exp";
            System.out.printf("%n=== Scenario heavy, Server B: %s ===%n", label);

            // INIZIALIZZA IL GENERATORE QUI.
            // In questo modo lo scenario Exp e lo scenario HyperExp partono con la STESSA identica sequenza (CRN).
            RandomGenerator rng = new RandomGenerator(masterSeed, hyperExp);

            double[][] allNB = new double[numReplications][numSamples];

            for (int r = 0; r < numReplications; r++) {
                // NIENTE NUOVO SEED QUI. Lascia che rng continui ininterrotto per le 30 repliche.
                Params params = new Params();
                params.lambda = lambda;
                params.is2FA_enabled = false;

                PSServer serverA = new PSServer(1.0, ServerState.IDLE, 0);
                PSServer serverB = new PSServer(1.0, ServerState.IDLE, 1);
                PSServer serverP = new PSServer(1.0, ServerState.IDLE, 2);

                SystemContext ctx = new SystemContext(params, rng, serverA, serverB, serverP);
                ctx.metrics.enableTransientSampling(sampleInterval);

                SimulationEngine engine = new SimulationEngine(ctx);
                engine.runForTime(horizon);

                TransientSampler ts = ctx.metrics.getTransientSampler();
                List<Double> nB = ts.getNB();
                int m = Math.min(numSamples, nB.size());
                for (int k = 0; k < m; k++) {
                    allNB[r][k] = nB.get(k);
                }

                if ((r + 1) % 10 == 0) {
                    System.out.printf("Replica %d/%d completata%n", r + 1, numReplications);
                }
            }

            String outPath = "heavy_" + label + ".csv";
            try (FileWriter fw = new FileWriter(outPath)) {
                fw.write("t,N_B_mean,N_B_hw\n");
                for (int k = 0; k < numSamples; k++) {
                    double t = (k + 1) * sampleInterval;
                    List<Double> sample = new ArrayList<>();
                    for (int r = 0; r < numReplications; r++) sample.add(allNB[r][k]);
                    BatchMeansAnalyzer.ConfidenceInterval ci = BatchMeansAnalyzer.computeCI(sample);
                    fw.write(String.format(Locale.US, "%.1f,%.6f,%.6f%n", t, ci.mean, ci.halfWidth));
                }
            }
            System.out.println("File esportato: " + new java.io.File(outPath).getAbsolutePath());
        }
    }
}
