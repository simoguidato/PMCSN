package metrics;

import model.ServerId;

import java.util.ArrayList;
import java.util.List;

public class TimeBatchCollector {
    private final double deltaT;
    private double batchStart;

    private double areaA = 0, areaB = 0, areaP = 0;
    private double busyA = 0, busyB = 0, busyP = 0;
    private int complA = 0, complB = 0, complP = 0;

    private final List<Double> batchMeansNA = new ArrayList<>();
    private final List<Double> batchMeansNB = new ArrayList<>();
    private final List<Double> batchMeansNP = new ArrayList<>();
    private final List<Double> batchMeansUA = new ArrayList<>();
    private final List<Double> batchMeansUB = new ArrayList<>();
    private final List<Double> batchMeansUP = new ArrayList<>();
    private final List<Double> batchMeansXA = new ArrayList<>();
    private final List<Double> batchMeansXB = new ArrayList<>();
    private final List<Double> batchMeansXP = new ArrayList<>();

    public TimeBatchCollector(double deltaT) {
        this(deltaT, 0.0);
    }

    /* startTime: istante di simulazione da cui iniziare a contare i batch  */
    public TimeBatchCollector(double deltaT, double startTime) {
        this.deltaT = deltaT;
        this.batchStart = startTime;
    }

    /* per ogni intervallo [startTs,endTs] del motore, con la size corrente di ciascun server. */
    public void advance(double startTs, double endTs, int sizeA, int sizeB, int sizeP) {
        double t = startTs;
        while (t < endTs) {
            double batchEnd = batchStart + deltaT;
            double segmentEnd = Math.min(endTs, batchEnd);
            double segment = segmentEnd - t;

            areaA += sizeA * segment;
            areaB += sizeB * segment;
            areaP += sizeP * segment;
            if (sizeA > 0) busyA += segment;
            if (sizeB > 0) busyB += segment;
            if (sizeP > 0) busyP += segment;

            if (segmentEnd >= batchEnd - 1e-9) {
                flushBatch();
                batchStart = batchEnd;
            }
            t = segmentEnd;
        }
    }

    private void flushBatch() {
        batchMeansNA.add(areaA / deltaT);
        batchMeansNB.add(areaB / deltaT);
        batchMeansNP.add(areaP / deltaT);
        batchMeansUA.add(busyA / deltaT);
        batchMeansUB.add(busyB / deltaT);
        batchMeansUP.add(busyP / deltaT);
        batchMeansXA.add(complA / deltaT);
        batchMeansXB.add(complB / deltaT);
        batchMeansXP.add(complP / deltaT);

        areaA = areaB = areaP = 0;
        busyA = busyB = busyP = 0;
        complA = complB = complP = 0;
    }

    /* ogni volta che un job lascia una stazione (per il throughput X per-server). */
    public void recordDeparture(ServerId server, double eventTime) {
        switch (server) {
            case SERVER_A: complA++; break;
            case SERVER_B: complB++; break;
            case SERVER_P: complP++; break;
        }
    }

    public List<Double> getBatchMeansNA() { return batchMeansNA; }
    public List<Double> getBatchMeansNB() { return batchMeansNB; }
    public List<Double> getBatchMeansNP() { return batchMeansNP; }
    public List<Double> getBatchMeansUA() { return batchMeansUA; }
    public List<Double> getBatchMeansUB() { return batchMeansUB; }
    public List<Double> getBatchMeansUP() { return batchMeansUP; }
    public List<Double> getBatchMeansXA() { return batchMeansXA; }
    public List<Double> getBatchMeansXB() { return batchMeansXB; }
    public List<Double> getBatchMeansXP() { return batchMeansXP; }
}