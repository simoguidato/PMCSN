package metrics;


import model.Job;

public class MetricsCollector {
    // Aree per il calcolo del numero medio di Job (N) nel sistema globale
    private double areaA = 0.0;
    private double areaB = 0.0;
    private double areaP = 0.0;

    // Contatori per il Tempo di Risposta Globale (R)
    private long totalJobsCompleted = 0;
    private double sumResponseTime = 0.0;

    public void updateAreas(double deltaT, int sizeA, int sizeB, int sizeP) {
        areaA += sizeA * deltaT;
        areaB += sizeB * deltaT;
        areaP += sizeP * deltaT;
    }

    public void recordJobCompleted(Job job, double currentClock) {
        totalJobsCompleted++;
        sumResponseTime += (currentClock - job.getArrivalTime());
    }

    public long getTotalJobsCompleted() {
        return totalJobsCompleted;
    }

    public double getAverageResponseTime() {
        if (totalJobsCompleted == 0) return 0.0;
        return sumResponseTime / totalJobsCompleted;
    }

    public double getAverageJobsInSystemA(double clock) { return areaA / clock; }
    public double getAverageJobsInSystemB(double clock) { return areaB / clock; }
    public double getAverageJobsInSystemP(double clock) { return areaP / clock; }

    // Metodo fondamentale per il Warm-up (scarto del transitorio)
    public void reset() {
        areaA = 0;
        areaB = 0;
        areaP = 0;
        totalJobsCompleted = 0;
        sumResponseTime = 0;
    }
}
