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
 * Simulazione a orizzonte finito per l'Obiettivo 3 (scenario heavy).
 * A lambda=1.4 req/s il sistema supera il throughput bound (X0=1.25 req/s, Sez. Collo di bottiglia):
 * non esiste uno stato stazionario, quindi non si scarta il transitorio (E' il fenomeno da osservare)
 * e si usano repliche indipendenti anziché batch means.
 */
public class HeavyLoadExperiment {

    public static void main(String[] args) throws Exception {

        double lambda = 1.4;
        double horizon = 150_000;     // orizzonte esteso, per verificare la convergenza del tasso di crescita
        double sampleInterval = 100;
        // repliche ridotte rispetto al run precedente (100): con N_B che cresce linearmente,
        // il costo per evento cresce con la lunghezza della coda -> costo totale ~ O(T^2).
        // 30 repliche restano sufficienti per un IC ragionevole, tenendo il tempo di calcolo gestibile.
        int numReplications = 50;

        int numSamples = (int) Math.floor(horizon / sampleInterval);
        double[][] allNA = new double[numReplications][numSamples];
        double[][] allNB = new double[numReplications][numSamples];
        double[][] allNP = new double[numReplications][numSamples];

        long masterSeed = 123456789L;
        RandomGenerator rng = new RandomGenerator(masterSeed);

        for (int r = 0; r < numReplications; r++) {
            Params params = new Params();
            params.lambda = lambda;
            params.is2FA_enabled = false;

            PSServer serverA = new PSServer(1.0, ServerState.IDLE, 0);
            PSServer serverB = new PSServer(1.0, ServerState.IDLE, 1);
            PSServer serverP = new PSServer(1.0, ServerState.IDLE, 2);

            // Passi lo stesso oggetto 'rng'. Non essendo reinizializzato,
            // la Replica 2 partirà ESATTAMENTE da dove ha finito la Replica 1.
            SystemContext ctx = new SystemContext(params, rng, serverA, serverB, serverP);
            ctx.metrics.enableTransientSampling(sampleInterval);

            SimulationEngine engine = new SimulationEngine(ctx);
            engine.runForTime(horizon);

            TransientSampler ts = ctx.metrics.getTransientSampler();
            List<Double> nA = ts.getNA();
            List<Double> nB = ts.getNB();
            List<Double> nP = ts.getNP();

            int m = Math.min(numSamples, nA.size());
            for (int k = 0; k < m; k++) {
                allNA[r][k] = nA.get(k);
                allNB[r][k] = nB.get(k);
                allNP[r][k] = nP.get(k);
            }

            if ((r + 1) % 10 == 0) {
                System.out.printf("Replica %d/%d completata%n", r + 1, numReplications);
            }
        }

        String outPath = "heavy_load_experiment.csv";
        try (FileWriter fw = new FileWriter(outPath)) {
            fw.write("t,N_A_mean,N_A_hw,N_B_mean,N_B_hw,N_P_mean,N_P_hw\n");

            for (int k = 0; k < numSamples; k++) {
                double t = (k + 1) * sampleInterval;

                List<Double> sampleA = new ArrayList<>();
                List<Double> sampleB = new ArrayList<>();
                List<Double> sampleP = new ArrayList<>();
                for (int r = 0; r < numReplications; r++) {
                    sampleA.add(allNA[r][k]);
                    sampleB.add(allNB[r][k]);
                    sampleP.add(allNP[r][k]);
                }
                BatchMeansAnalyzer.ConfidenceInterval ciA = BatchMeansAnalyzer.computeCI(sampleA);
                BatchMeansAnalyzer.ConfidenceInterval ciB = BatchMeansAnalyzer.computeCI(sampleB);
                BatchMeansAnalyzer.ConfidenceInterval ciP = BatchMeansAnalyzer.computeCI(sampleP);

                fw.write(String.format(Locale.US, "%.1f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                        t, ciA.mean, ciA.halfWidth, ciB.mean, ciB.halfWidth, ciP.mean, ciP.halfWidth));
            }
        }

        System.out.println("\nFile esportato: " + new java.io.File(outPath).getAbsolutePath());
    }
}