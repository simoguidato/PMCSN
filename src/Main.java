import controllers.PSServer;
import engine.SimulationEngine;
import engine.SystemContext;
import metrics.BatchMeansAnalyzer;
import metrics.TimeBatchCollector;
import model.ServerState;
import utils.Params;
import utils.RandomGenerator;

import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {

        long seed = 123456789L;
        double lambda = 1.2;
        int batchSize = 8000;
        int targetBatches = 100;
        long maxJobs = (long) batchSize * targetBatches;

        Params params = new Params();
        params.lambda = lambda;
        params.is2FA_enabled = false;

        RandomGenerator rng = new RandomGenerator(seed);

        PSServer serverA = new PSServer(1.0, ServerState.IDLE, 0);
        PSServer serverB = new PSServer(1.0, ServerState.IDLE, 1);
        PSServer serverP = new PSServer(1.0, ServerState.IDLE, 2);

        SystemContext ctx = new SystemContext(params, rng, serverA, serverB, serverP);

        double deltaT = batchSize / lambda;
        ctx.metrics.enableTimeBatching(deltaT);

        // [NUOVO] Abilita il tracking per-job su file
        ctx.metrics.enableJobTracing("trace_visite_job.csv");

        SimulationEngine engine = new SimulationEngine(ctx);
        engine.run(maxJobs);

        // [NUOVO] Chiudi il file alla fine
        ctx.metrics.closeTracing();

        double clock = engine.getClock();
        System.out.println("\n--- Run completata ---");
        System.out.printf("Job completati: %d, Tempo simulato: %.2f s%n", ctx.metrics.getTotalJobsCompleted(), clock);

        List<Double> batchMeansR = BatchMeansAnalyzer.batchMeansFromSequence(ctx.metrics.getResponseTimesSystem(), batchSize);
        BatchMeansAnalyzer.ConfidenceInterval ciR = BatchMeansAnalyzer.computeCI(batchMeansR);
        System.out.println("\nR (sistema): " + ciR);

        TimeBatchCollector tb = ctx.metrics.getTimeBatchCollector();

        System.out.println("\n-- Server A --");
        System.out.println("N_A: " + BatchMeansAnalyzer.computeCI(tb.getBatchMeansNA()));
        System.out.println("U_A: " + BatchMeansAnalyzer.computeCI(tb.getBatchMeansUA()));
        System.out.println("X_A: " + BatchMeansAnalyzer.computeCI(tb.getBatchMeansXA()));

        System.out.println("\n-- Server B --");
        System.out.println("N_B: " + BatchMeansAnalyzer.computeCI(tb.getBatchMeansNB()));
        System.out.println("U_B: " + BatchMeansAnalyzer.computeCI(tb.getBatchMeansUB()));
        System.out.println("X_B: " + BatchMeansAnalyzer.computeCI(tb.getBatchMeansXB()));

        System.out.println("\n-- Server P --");
        System.out.println("N_P: " + BatchMeansAnalyzer.computeCI(tb.getBatchMeansNP()));
        System.out.println("U_P: " + BatchMeansAnalyzer.computeCI(tb.getBatchMeansUP()));
        System.out.println("X_P: " + BatchMeansAnalyzer.computeCI(tb.getBatchMeansXP()));

        System.out.println("\nFile di Tracciamento generato: trace_visite_job.csv");
    }
}