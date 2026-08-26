package analysis;

import controllers.PSServer;
import engine.SimulationEngine;
import engine.SystemContext;
import metrics.TransientSampler;
import model.ServerState;
import utils.Params;
import utils.RandomGenerator;

import java.io.FileWriter;
import java.util.List;

public class TransientAnalysis {

    // Stessi 5 seed per ogni lambda, per confrontare "a parità di casualità" quanto cambia la velocità di assestamento
    private static final long MASTER_SEED = 123456789L;
    private static final double SAMPLE_INTERVAL = 100; // campionamento di N(t) ogni 100s
    private static final int NUM_REPLICHE = 64;
    public static void main(String[] args) throws Exception {

        // lambda=1.2: punto più critico dello scan (Obiettivo 1), il caso che determina la soglia di warm-up
        //             da usare per l'intero scan 0.5-1.2
        // lambda=0.5: per il confronto (rho_B=0.4, rilassamento atteso molto piu' rapido)
        runTransientAnalysis(1.2, 150_000, "transient_analysis_lambda1.2.csv");
        runTransientAnalysis(0.5, 150_000, "transient_analysis_lambda0.5.csv");
    }

    private static void runTransientAnalysis(double lambda, double horizon, String outPath) throws Exception {

        System.out.printf("%n=== Analisi del transitorio, lambda=%.2f ===%n", lambda);

        int numSamples = (int) Math.floor(horizon / SAMPLE_INTERVAL);
        double[][] allNA = new double[NUM_REPLICHE][numSamples];
        double[][] allNB = new double[NUM_REPLICHE][numSamples];
        double[][] allNP = new double[NUM_REPLICHE][numSamples];
        RandomGenerator rng = new RandomGenerator(MASTER_SEED);
        for (int r = 0; r < NUM_REPLICHE; r++) {
            Params params = new Params();
            params.lambda = lambda;
            params.is2FA_enabled = false;

            // 3. PASSI LO STESSO RNG SENZA REINIZIALIZZARLO!
            // Il generatore continuerà da dove si era fermato alla replica precedente
            PSServer serverA = new PSServer(1.0, ServerState.IDLE, 0);
            PSServer serverB = new PSServer(1.0, ServerState.IDLE, 1);
            PSServer serverP = new PSServer(1.0, ServerState.IDLE, 2);

            SystemContext ctx = new SystemContext(params, rng, serverA, serverB, serverP);
            ctx.metrics.enableTransientSampling(SAMPLE_INTERVAL);

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

            System.out.printf("Replica %d/%d (seed=%d) completata: %d job, clock finale=%.1f%n",
                    r + 1, NUM_REPLICHE, MASTER_SEED, ctx.metrics.getTotalJobsCompleted(), engine.getClock());
        }

        // Media d'insieme (ensemble average) sulle repliche, per ciascun istante campionato
        String outPathSeeds = outPath.replace(".csv", "_seeds.csv");
        try (FileWriter fw = new FileWriter(outPathSeeds)) {
            // Intestazione con i 5 seed e la media
            fw.write("t,N_B_1,N_B_2,N_B_3,N_B_4,N_B_5,N_B_mean\n");
            for (int k = 0; k < numSamples; k++) {
                double t = (k + 1) * SAMPLE_INTERVAL;
                double mB = 0;
                StringBuilder line = new StringBuilder();
                line.append(t);

                // Scrive i 5 valori individuali del Server B per questo istante 't'
                for (int r = 0; r < NUM_REPLICHE; r++) {
                    line.append(",").append(allNB[r][k]);
                    mB += allNB[r][k];
                }

                // Calcola e aggiunge la media d'insieme in fondo alla riga
                mB /= NUM_REPLICHE;
                line.append(",").append(mB).append("\n");

                fw.write(line.toString());
            }
        }
        System.out.println("File con i seed esportato: " + new java.io.File(outPathSeeds).getAbsolutePath());
    }
}