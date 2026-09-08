package analysis.finite_horizon;

import controllers.PSServer;
import engine.SimulationEngine;
import engine.SystemContext;
import metrics.BatchMeansAnalyzer;
import model.ServerState;
import utils.Params;
import utils.RandomGenerator;
import utils.RngSeedLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Esperimento dedicato ESCLUSIVAMENTE alla visualizzazione della convergenza
 * del transitorio, nello stile dei grafici multi-replica usati nella relazione
 * dei colleghi.
 *
 * Non sostituisce TransientAnalysis / Welch e NON modifica il warm-up scelto.
 * Serve a produrre, per 64 realizzazioni, le traiettorie temporali di:
 *
 *   - tempo medio di risposta del sistema R;
 *   - utilizzazione del Server B U_B;
 *   - popolazione media del Server B N_B.
 *
 * Per ogni metrica vengono esportate sia:
 *
 *   1) stime cumulative dall'istante 0 fino a t;
 *   2) stime locali sulla finestra ((k-1)Delta, kDelta].
 *
 * Le stime locali permettono poi allo script Python di applicare una media
 * mobile e mostrare chiaramente l'assestamento senza trascinarsi per sempre
 * l'initialization bias dei primi istanti.
 */
public class ConvergenceVisualizationExperiment {

    private static final long MASTER_SEED = 123456789L;

    private static final double[] LAMBDAS = {
            0.50,
            1.20
    };

    private static final double HORIZON = 150_000.0;
    private static final double SAMPLE_INTERVAL = 100.0;
    private static final int NUM_REALIZATIONS = 64;
    private static final boolean SERVER_B_HYPEREXP = true;

    private static final String CSV_DIR = "csv";
    private static final String SEED_FILE =
            CSV_DIR + "/convergence_visualization_rng_seeds.csv";
    private static final String SUMMARY_FILE =
            CSV_DIR + "/convergence_visualization_summary.csv";


    public static void main(String[] args) throws Exception {

        File csvDir = new File(CSV_DIR);
        if (!csvDir.exists() && !csvDir.mkdirs()) {
            throw new IllegalStateException(
                    "Impossibile creare la directory: "
                            + csvDir.getAbsolutePath());
        }

        deleteIfExists(new File(SEED_FILE));
        deleteIfExists(new File(SUMMARY_FILE));

        try (FileWriter summaryWriter =
                     new FileWriter(SUMMARY_FILE)) {

            summaryWriter.write(
                    "lambda,metric,mean,half_width_95,theory,num_realizations\n");

            for (double lambda : LAMBDAS) {
                runConfiguration(lambda, summaryWriter);
            }
        }

        System.out.println("\n========================================");
        System.out.println("Convergence visualization completata.");
        System.out.println("========================================");
        System.out.println("Seed RNG: "
                + new File(SEED_FILE).getAbsolutePath());
        System.out.println("Summary: "
                + new File(SUMMARY_FILE).getAbsolutePath());
    }


    private static void runConfiguration(
            double lambda,
            FileWriter summaryWriter) throws Exception {

        System.out.println("\n========================================");
        System.out.printf(
                Locale.US,
                "CONVERGENZA MULTI-REPLICA - lambda = %.2f%n",
                lambda);
        System.out.println("scenario = 1FA");
        System.out.println("B = HyperExp CV=2");
        System.out.println("realizzazioni = " + NUM_REALIZATIONS);
        System.out.println("orizzonte = " + HORIZON + " s");
        System.out.println("Delta campionamento = "
                + SAMPLE_INTERVAL + " s");
        System.out.println("========================================");

        int numSamples =
                (int) Math.floor(HORIZON / SAMPLE_INTERVAL);

        double[][] rCumulative =
                new double[NUM_REALIZATIONS][numSamples];
        double[][] uBCumulative =
                new double[NUM_REALIZATIONS][numSamples];
        double[][] nBCumulative =
                new double[NUM_REALIZATIONS][numSamples];

        double[][] rWindow =
                new double[NUM_REALIZATIONS][numSamples];
        double[][] uBWindow =
                new double[NUM_REALIZATIONS][numSamples];
        double[][] nBWindow =
                new double[NUM_REALIZATIONS][numSamples];

        /*
         * NUOVA CONFIGURAZIONE -> si riparte dal master seed.
         *
         * All'interno delle 64 realizzazioni, invece, lo stesso RNG
         * continua senza alcun reseed, consumando segmenti successivi
         * della sequenza pseudocasuale.
         */
        RandomGenerator rng =
                new RandomGenerator(
                        MASTER_SEED,
                        SERVER_B_HYPEREXP);

        for (int r = 0; r < NUM_REALIZATIONS; r++) {
            RngSeedLogger.append(
                    SEED_FILE,
                    "ConvergenceVisualizationExperiment",
                    String.format(Locale.US,
                            "1FA_lambda_%.2f_HyperExp", lambda),
                    r,
                    rng);

            Params params = new Params();
            params.lambda = lambda;
            params.is2FA_enabled = false;

            PSServer serverA =
                    new PSServer(1.0, ServerState.IDLE, 0);
            PSServer serverB =
                    new PSServer(1.0, ServerState.IDLE, 1);
            PSServer serverP =
                    new PSServer(1.0, ServerState.IDLE, 2);

            SystemContext ctx =
                    new SystemContext(
                            params,
                            rng,
                            serverA,
                            serverB,
                            serverP);

            SimulationEngine engine =
                    new SimulationEngine(ctx);

            /*
             * Stato cumulativo necessario per ricostruire le statistiche
             * della singola finestra senza modificare il simulatore.
             */
            double previousBusyAreaB = 0.0;
            double previousPopulationAreaB = 0.0;
            int previousResponseIndex = 0;

            PrintStream originalOut = System.out;
            PrintStream silentOut =
                    new PrintStream(OutputStream.nullOutputStream());

            try {
                System.setOut(silentOut);

                for (int k = 0; k < numSamples; k++) {

                    double t =
                            (k + 1) * SAMPLE_INTERVAL;

                    /*
                     * runForTime usa un orizzonte ASSOLUTO.
                     * Chiamandolo con 100, 200, 300, ... la stessa run
                     * viene semplicemente fatta avanzare a intervalli.
                     */
                    engine.runForTime(t);

                    double clock = engine.getClock();

                    /* =================================================
                     * STIME CUMULATIVE [0,t]
                     * ================================================= */

                    double cumulativeR =
                            ctx.metrics.getAverageResponseTime();

                    double cumulativeUB =
                            ctx.metrics.getUtilizationB(clock);

                    double cumulativeNB =
                            ctx.metrics.getAverageJobsInSystemB(clock);

                    rCumulative[r][k] = cumulativeR;
                    uBCumulative[r][k] = cumulativeUB;
                    nBCumulative[r][k] = cumulativeNB;

                    /* =================================================
                     * STIME LOCALI SULLA FINESTRA PRECEDENTE
                     * =================================================
                     *
                     * U_B(t) = busyArea_B(0,t) / t
                     * N_B(t) = populationArea_B(0,t) / t
                     */

                    double currentBusyAreaB =
                            cumulativeUB * clock;

                    double currentPopulationAreaB =
                            cumulativeNB * clock;

                    uBWindow[r][k] =
                            (currentBusyAreaB - previousBusyAreaB)
                                    / SAMPLE_INTERVAL;

                    nBWindow[r][k] =
                            (currentPopulationAreaB
                                    - previousPopulationAreaB)
                                    / SAMPLE_INTERVAL;

                    previousBusyAreaB = currentBusyAreaB;
                    previousPopulationAreaB =
                            currentPopulationAreaB;

                    /*
                     * Per R prendiamo esclusivamente i job completati
                     * nella finestra corrente.
                     */
                    List<Double> responses =
                            ctx.metrics.getResponseTimesSystem();

                    int currentResponseIndex = responses.size();

                    if (currentResponseIndex
                            > previousResponseIndex) {

                        double sum = 0.0;

                        for (int i = previousResponseIndex;
                             i < currentResponseIndex;
                             i++) {

                            sum += responses.get(i);
                        }

                        rWindow[r][k] =
                                sum /
                                        (currentResponseIndex
                                                - previousResponseIndex);

                    } else {
                        rWindow[r][k] = Double.NaN;
                    }

                    previousResponseIndex =
                            currentResponseIndex;
                }

            } finally {
                System.setOut(originalOut);
                silentOut.close();
            }

            if ((r + 1) % 8 == 0
                    || r == NUM_REALIZATIONS - 1) {

                System.out.printf(
                        Locale.US,
                        "lambda %.2f - realizzazione %d/%d completata%n",
                        lambda,
                        r + 1,
                        NUM_REALIZATIONS);
            }
        }

        String suffix =
                String.format(Locale.US, "%.2f", lambda);

        String outPath =
                CSV_DIR + "/convergence_lambda" + suffix + ".csv";

        writeWideCsv(
                outPath,
                rCumulative,
                uBCumulative,
                nBCumulative,
                rWindow,
                uBWindow,
                nBWindow,
                numSamples);

        writeFinalSummary(
                lambda,
                rCumulative,
                uBCumulative,
                nBCumulative,
                numSamples,
                summaryWriter);

        System.out.println("CSV: "
                + new File(outPath).getAbsolutePath());
    }


    private static void writeWideCsv(
            String path,
            double[][] rCumulative,
            double[][] uBCumulative,
            double[][] nBCumulative,
            double[][] rWindow,
            double[][] uBWindow,
            double[][] nBWindow,
            int numSamples) throws Exception {

        try (FileWriter fw = new FileWriter(path)) {

            StringBuilder header = new StringBuilder("t");

            appendMetricHeader(header, "R_cum");
            appendMetricHeader(header, "U_B_cum");
            appendMetricHeader(header, "N_B_cum");

            appendMetricHeader(header, "R_window");
            appendMetricHeader(header, "U_B_window");
            appendMetricHeader(header, "N_B_window");

            header.append("\n");
            fw.write(header.toString());

            for (int k = 0; k < numSamples; k++) {

                double t =
                        (k + 1) * SAMPLE_INTERVAL;

                StringBuilder line =
                        new StringBuilder(
                                String.format(Locale.US, "%.1f", t));

                appendMetricValues(line, rCumulative, k);
                appendMetricValues(line, uBCumulative, k);
                appendMetricValues(line, nBCumulative, k);

                appendMetricValues(line, rWindow, k);
                appendMetricValues(line, uBWindow, k);
                appendMetricValues(line, nBWindow, k);

                line.append("\n");
                fw.write(line.toString());
            }
        }
    }


    private static void appendMetricHeader(
            StringBuilder header,
            String metricName) {

        for (int r = 1; r <= NUM_REALIZATIONS; r++) {
            header.append(",")
                    .append(metricName)
                    .append("_")
                    .append(r);
        }

        header.append(",")
                .append(metricName)
                .append("_mean");
    }


    private static void appendMetricValues(
            StringBuilder line,
            double[][] values,
            int sampleIndex) {

        double sum = 0.0;
        int valid = 0;

        for (int r = 0; r < NUM_REALIZATIONS; r++) {

            double value =
                    values[r][sampleIndex];

            if (Double.isNaN(value)) {
                line.append(",NaN");
            } else {
                line.append(String.format(
                        Locale.US,
                        ",%.9f",
                        value));

                sum += value;
                valid++;
            }
        }

        double mean =
                valid == 0
                        ? Double.NaN
                        : sum / valid;

        if (Double.isNaN(mean)) {
            line.append(",NaN");
        } else {
            line.append(String.format(
                    Locale.US,
                    ",%.9f",
                    mean));
        }
    }


    private static void writeFinalSummary(
            double lambda,
            double[][] rCumulative,
            double[][] uBCumulative,
            double[][] nBCumulative,
            int numSamples,
            FileWriter fw) throws Exception {

        int last = numSamples - 1;

        List<Double> finalR =
                getColumn(rCumulative, last);

        List<Double> finalUB =
                getColumn(uBCumulative, last);

        List<Double> finalNB =
                getColumn(nBCumulative, last);

        BatchMeansAnalyzer.ConfidenceInterval ciR =
                BatchMeansAnalyzer.computeCI(finalR);

        BatchMeansAnalyzer.ConfidenceInterval ciUB =
                BatchMeansAnalyzer.computeCI(finalUB);

        BatchMeansAnalyzer.ConfidenceInterval ciNB =
                BatchMeansAnalyzer.computeCI(finalNB);

        double rhoB = lambda * 0.8;

        double theoryUB = rhoB;
        double theoryNB = rhoB / (1.0 - rhoB);

        /*
         * BCMP / PS, scenario 1FA:
         * D_A=0.7, D_B=0.8, D_P=0.4.
         */
        double theoryR =
                0.7 / (1.0 - lambda * 0.7)
                        + 0.8 / (1.0 - lambda * 0.8)
                        + 0.4 / (1.0 - lambda * 0.4);

        writeSummaryLine(
                fw, lambda, "R", ciR, theoryR);

        writeSummaryLine(
                fw, lambda, "U_B", ciUB, theoryUB);

        writeSummaryLine(
                fw, lambda, "N_B", ciNB, theoryNB);

        fw.flush();

        System.out.printf(
                Locale.US,
                "Finale t=%.0f s | R=%.6f +/- %.6f "
                        + "| U_B=%.6f +/- %.6f "
                        + "| N_B=%.6f +/- %.6f%n",
                HORIZON,
                ciR.mean,
                ciR.halfWidth,
                ciUB.mean,
                ciUB.halfWidth,
                ciNB.mean,
                ciNB.halfWidth);
    }


    private static void writeSummaryLine(
            FileWriter fw,
            double lambda,
            String metric,
            BatchMeansAnalyzer.ConfidenceInterval ci,
            double theory) throws Exception {

        fw.write(String.format(
                Locale.US,
                "%.2f,%s,%.9f,%.9f,%.9f,%d%n",
                lambda,
                metric,
                ci.mean,
                ci.halfWidth,
                theory,
                NUM_REALIZATIONS));
    }


    private static List<Double> getColumn(
            double[][] matrix,
            int column) {

        List<Double> values =
                new ArrayList<>(NUM_REALIZATIONS);

        for (int r = 0; r < NUM_REALIZATIONS; r++) {
            values.add(matrix[r][column]);
        }

        return values;
    }


    private static void deleteIfExists(File file) {
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException(
                    "Impossibile eliminare il vecchio file: "
                            + file.getAbsolutePath());
        }
    }
}
