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

        // --- Parametri della run ---
        long seed = 123456789L;
        double lambda = 1.2;      // punto operativo più critico (1FA)
        int batchSize = 8000;     // fissata allo step 4 (autocorrelazione, caso più conservativo)
        int targetBatches = 100;  // vogliamo almeno un centinaio di batch per un IC affidabile
        long maxJobs = (long) batchSize * targetBatches;

        // --- Setup del sistema ---
        Params params = new Params();
        params.lambda = lambda;
        params.is2FA_enabled = false; // scenario base (1FA)

        RandomGenerator rng = new RandomGenerator(seed);

        // Capacità = 1 => ogni server è un unico nodo Processor Sharing
        PSServer serverA = new PSServer(1.0, ServerState.IDLE, 0);
        PSServer serverB = new PSServer(1.0, ServerState.IDLE, 1);
        PSServer serverP = new PSServer(1.0, ServerState.IDLE, 2);

        SystemContext ctx = new SystemContext(params, rng, serverA, serverB, serverP);

        // ΔT per il batching temporale di N, U, X: tempo medio per generare `batchSize` arrivi
        double deltaT = batchSize / lambda;
        ctx.metrics.enableTimeBatching(deltaT);

        SimulationEngine engine = new SimulationEngine(ctx);

        // --- Esecuzione ---
        engine.run(maxJobs);

        double clock = engine.getClock();
        System.out.println("\n--- Run completata ---");
        System.out.printf("Job completati: %d, Tempo simulato: %.2f s%n", ctx.metrics.getTotalJobsCompleted(), clock);

        // --- IC per il tempo di risposta di sistema R (batch means job-indicizzati) ---
        List<Double> batchMeansR = BatchMeansAnalyzer.batchMeansFromSequence(ctx.metrics.getResponseTimesSystem(), batchSize);
        BatchMeansAnalyzer.ConfidenceInterval ciR = BatchMeansAnalyzer.computeCI(batchMeansR);
        System.out.println("\nR (sistema): " + ciR);

        // --- IC per N, U, X per-server (batch means temporali) ---
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

        // --- Export sequenze grezze (facoltativo, utile per ricontrollare l'ACF su questa run più lunga) ---
        ctx.metrics.exportSequenceToCsv("response_times_system.csv", ctx.metrics.getResponseTimesSystem());
        ctx.metrics.exportSequenceToCsv("response_times_B.csv", ctx.metrics.getResponseTimesB());
        System.out.println("\nFile esportati (percorso assoluto):");
        System.out.println(" - " + new java.io.File("response_times_system.csv").getAbsolutePath());
        System.out.println(" - " + new java.io.File("response_times_B.csv").getAbsolutePath());
    }
}