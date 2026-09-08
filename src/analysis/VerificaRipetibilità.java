package analysis;

import controllers.PSServer;
import engine.SimulationEngine;
import engine.SystemContext;
import metrics.BatchMeansAnalyzer;
import metrics.TimeBatchCollector;
import model.ServerState;
import utils.Params;
import utils.RandomGenerator;

public class VerificaRipetibilità {

    private static class Result {
        double r, nB, xB;
    }

    private static Result runSimulation(long seed) {
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

        Result res = new Result();
        res.r = BatchMeansAnalyzer.computeCI(
                BatchMeansAnalyzer.batchMeansFromSequence(ctx.metrics.getResponseTimesSystem(), batchSize)).mean;
        TimeBatchCollector tb = ctx.metrics.getTimeBatchCollector();
        res.nB = BatchMeansAnalyzer.computeCI(tb.getBatchMeansNB()).mean;
        res.xB = BatchMeansAnalyzer.computeCI(tb.getBatchMeansXB()).mean;
        return res;
    }

    public static void main(String[] args) {
        long seed = 123456789L;

        System.out.println("=== Verifica di Riproducibilita' Deterministica ===");
        Result r1 = runSimulation(seed);
        Result r2 = runSimulation(seed);

        System.out.printf("Metrica       Run 1           Run 2           Diff%n");
        System.out.printf("E[R]   [s]    %.6f        %.6f        %.6f%n", r1.r,  r2.r,  Math.abs(r1.r - r2.r));
        System.out.printf("E[N_B] [job]  %.6f        %.6f        %.6f%n", r1.nB, r2.nB, Math.abs(r1.nB - r2.nB));
        System.out.printf("X_B    [r/s]  %.6f        %.6f        %.6f%n", r1.xB, r2.xB, Math.abs(r1.xB - r2.xB));
    }
}