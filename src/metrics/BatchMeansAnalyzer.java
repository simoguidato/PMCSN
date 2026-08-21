package metrics;

import java.util.ArrayList;
import java.util.List;

public class BatchMeansAnalyzer {
    private BatchMeansAnalyzer() {
        /* This utility class should not be instantiated */
    }


    public static class ConfidenceInterval {
        public final double mean;
        public final double halfWidth;
        public final int numBatches;

        public ConfidenceInterval(double mean, double halfWidth, int numBatches) {
            this.mean = mean;
            this.halfWidth = halfWidth;
            this.numBatches = numBatches;
        }

        public double getLower() { return mean - halfWidth; }
        public double getUpper() { return mean + halfWidth; }

        @Override
        public String toString() {
            return String.format("%.4f ± %.4f (IC95%%, m=%d batch)", mean, halfWidth, numBatches);
        }
    }

    /* --Divide una sequenza job-indicizzata in batch di dimensione fissa.-- */
    public static List<Double> batchMeansFromSequence(List<Double> observations, int batchSize) {
        List<Double> means = new ArrayList<>();
        int n = observations.size();
        int numBatches = n / batchSize;
        for (int b = 0; b < numBatches; b++) {
            double sum = 0;
            for (int i = b * batchSize; i < (b + 1) * batchSize; i++) {
                sum += observations.get(i);
            }
            means.add(sum / batchSize);
        }
        return means;
    }

    /* --Media e IC al 95% a partire da medie di batch.-- */
    public static ConfidenceInterval computeCI(List<Double> batchMeans) {
        int m = batchMeans.size();
        if (m < 2) throw new IllegalArgumentException("Servono almeno 2 batch per calcolare un IC.");

        double mean = 0;
        for (double x : batchMeans) mean += x;
        mean /= m;

        double variance = 0;
        for (double x : batchMeans) variance += (x - mean) * (x - mean);
        variance /= (m - 1);

        double stdErr = Math.sqrt(variance / m);
        double tValue = studentT975(m - 1);
        double halfWidth = tValue * stdErr;

        return new ConfidenceInterval(mean, halfWidth, m);
    }

    /**
     * Approssimazione del quantile t di Student al 97.5% (IC al 95% bilatero),
     * espansione al primo ordine. Accurata per df >= 20 (errore < 0.5%)
     */
    public static double studentT975(int df) {
        final double z = 1.959964; // quantile normale standard al 97.5%
        return z + (Math.pow(z, 3) + z) / (4.0 * df);
    }
}
