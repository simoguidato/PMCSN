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

    public static void main(String[] args) throws Exception {

        double lambda = 1.2;          // punto operativo più critico (1FA)
        double horizon = 60_000;      // orizzonte temporale di osservazione [s]
        double sampleInterval = 100;  // campionamento di N(t) ogni 100s
        long[] seeds = {123456789L, 987654321L, 111111111L, 222222222L, 555555555L};

        int numSamples = (int) Math.floor(horizon / sampleInterval);
        double[][] allNA = new double[seeds.length][numSamples];
        double[][] allNB = new double[seeds.length][numSamples];
        double[][] allNP = new double[seeds.length][numSamples];

        for (int r = 0; r < seeds.length; r++) {
            Params params = new Params();
            params.lambda = lambda;
            params.is2FA_enabled = false;

            RandomGenerator rng = new RandomGenerator(seeds[r]);
            PSServer serverA = new PSServer(1.0, ServerState.IDLE, 0);
            PSServer serverB = new PSServer(1.0, ServerState.IDLE, 1);
            PSServer serverP = new PSServer(1.0, ServerState.IDLE, 2);

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

            System.out.printf("Replica %d/%d (seed=%d) completata: %d job, clock finale=%.1f%n",
                    r + 1, seeds.length, seeds[r], ctx.metrics.getTotalJobsCompleted(), engine.getClock());
        }

        // Media d'insieme (ensemble average) sulle repliche, per ciascun istante campionato
        String outPath = "transient_analysis.csv";
        try (FileWriter fw = new FileWriter(outPath)) {
            fw.write("t,N_A,N_B,N_P\n");
            for (int k = 0; k < numSamples; k++) {
                double t = (k + 1) * sampleInterval;
                double mA = 0, mB = 0, mP = 0;
                for (int r = 0; r < seeds.length; r++) {
                    mA += allNA[r][k];
                    mB += allNB[r][k];
                    mP += allNP[r][k];
                }
                mA /= seeds.length;
                mB /= seeds.length;
                mP /= seeds.length;
                fw.write(t + "," + mA + "," + mB + "," + mP + "\n");
            }
        }

        System.out.println("\nFile esportato: " + new java.io.File(outPath).getAbsolutePath());
    }
}
