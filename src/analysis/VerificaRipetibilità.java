package analysis;

import controllers.PSServer;
import engine.SimulationEngine;
import engine.SystemContext;
import metrics.BatchMeansAnalyzer;
import metrics.TimeBatchCollector;
import model.ServerState;
import utils.Params;
import utils.RandomGenerator;

/**
 * Verifica di riproducibilità: riesegue esattamente lo stesso setup usato in RegimeExperiment
 * per lambda=1.2, 1FA (stesso seed, stessa soglia di warm-up, stessa batch size), e stampa
 * gli stessi valori puntuali già ottenuti in precedenza, per un confronto diretto.
 * Se il sistema RNG multi-stream è corretto e deterministico, i valori devono coincidere
 * esattamente (entro la precisione dei double) con quelli della run originale.
 */
public class VerificaRipetibilità {

    public static void main(String[] args) throws Exception {

        long seed = 123456789L;
        double lambda = 1.2;
        double warmupTime = 100_000;
        int batchSize = 8000;
        long jobsAfterWarmup = (long) batchSize * 100;

        Params params = new Params();
        params.lambda = lambda;
        params.is2FA_enabled = false;

        RandomGenerator rng = new RandomGenerator(seed);
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
        BatchMeansAnalyzer.ConfidenceInterval ciXB = BatchMeansAnalyzer.computeCI(tb.getBatchMeansXB());

        System.out.println("=== Verifica di ripetibilita' (seed=123456789, lambda=1.2, 1FA) ===");
        System.out.printf("R  = %.10f  (originale: 24.4530080000)%n", ciR.mean);
        System.out.printf("N_B = %.10f  (originale: 23.2315690000)%n", ciNB.mean);
        System.out.printf("X_B = %.10f  (originale: 1.1991410000)%n", ciXB.mean);
    }
}
