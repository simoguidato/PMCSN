package metrics;

import java.util.ArrayList;
import java.util.List;

public class TransientSampler {
    private final double sampleInterval;
    private double nextSampleBoundary;

    private double areaA = 0, areaB = 0, areaP = 0;

    private final List<Double> nA = new ArrayList<>();
    private final List<Double> nB = new ArrayList<>();
    private final List<Double> nP = new ArrayList<>();

    public TransientSampler(double sampleInterval) {
        this.sampleInterval = sampleInterval;
        this.nextSampleBoundary = sampleInterval;
    }

    /** Da chiamare per ogni intervallo [startTs,endTs] del motore, con la size corrente di ciascun server. */
    public void advance(double startTs, double endTs, int sizeA, int sizeB, int sizeP) {
        double t = startTs;
        while (t < endTs) {
            double segmentEnd = Math.min(endTs, nextSampleBoundary);
            double segment = segmentEnd - t;

            areaA += sizeA * segment;
            areaB += sizeB * segment;
            areaP += sizeP * segment;

            if (segmentEnd >= nextSampleBoundary - 1e-9) {
                nA.add(areaA / sampleInterval);
                nB.add(areaB / sampleInterval);
                nP.add(areaP / sampleInterval);

                areaA = areaB = areaP = 0;
                nextSampleBoundary += sampleInterval;
            }
            t = segmentEnd;
        }
    }

    public List<Double> getNA() { return nA; }
    public List<Double> getNB() { return nB; }
    public List<Double> getNP() { return nP; }
}