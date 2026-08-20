package metrics;


import model.Job;
import model.ServerId;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MetricsCollector {
    // Aree per il calcolo del numero medio di Job (N) nel sistema globale (sull'intera run)
    private double areaA = 0.0;
    private double areaB = 0.0;
    private double areaP = 0.0;

    // Tempo di occupazione (size >= 1) sull'intera run, per l'utilizzazione U per-server
    private double busyTimeA = 0.0;
    private double busyTimeB = 0.0;
    private double busyTimeP = 0.0;

    // Conteggio completamenti per-server sull'intera run, per il throughput X
    private long totalComplA = 0;
    private long totalComplB = 0;
    private long totalComplP = 0;

    // Contatori per il Tempo di Risposta Globale (R)
    private long totalJobsCompleted = 0;
    private double sumResponseTime = 0.0;

    // Sequenze job-per-job (servono per l'analisi dell'autocorrelazione / batch means)
    private final List<Double> responseTimesSystem = new ArrayList<>(); // R del sistema, per job completato
    private final List<Double> responseTimesB = new ArrayList<>();      // tempo di sosta per-visita al Server B

    // Batching temporale per N, U, X per-server (per gli IC via batch means)
    private TimeBatchCollector timeBatchCollector = null;

    // Campionamento a intervalli fissi di N(t) per-server (per l'analisi del transitorio)
    private TransientSampler transientSampler = null;

    /** Attiva la raccolta a batch temporali fissi (durata deltaT) per N, U, X per-server. */
    public void enableTimeBatching(double deltaT) {
        this.timeBatchCollector = new TimeBatchCollector(deltaT);
    }

    public TimeBatchCollector getTimeBatchCollector() { return timeBatchCollector; }

    /** Attiva il campionamento di N(t) a intervalli fissi (per l'analisi del transitorio). */
    public void enableTransientSampling(double sampleInterval) {
        this.transientSampler = new TransientSampler(sampleInterval);
    }

    public TransientSampler getTransientSampler() { return transientSampler; }

    public void updateAreas(double startTs, double endTs, int sizeA, int sizeB, int sizeP) {
        double deltaT = endTs - startTs;
        areaA += sizeA * deltaT;
        areaB += sizeB * deltaT;
        areaP += sizeP * deltaT;
        if (sizeA > 0) busyTimeA += deltaT;
        if (sizeB > 0) busyTimeB += deltaT;
        if (sizeP > 0) busyTimeP += deltaT;

        if (timeBatchCollector != null) {
            timeBatchCollector.advance(startTs, endTs, sizeA, sizeB, sizeP);
        }
        if (transientSampler != null) {
            transientSampler.advance(startTs, endTs, sizeA, sizeB, sizeP);
        }
    }

    /** Da chiamare ogni volta che un job lascia una stazione (per il throughput X per-server). */
    public void recordDeparture(ServerId server, double eventTime) {
        switch (server) {
            case SERVER_A: totalComplA++; break;
            case SERVER_B: totalComplB++; break;
            case SERVER_P: totalComplP++; break;
        }
        if (timeBatchCollector != null) {
            timeBatchCollector.recordDeparture(server, eventTime);
        }
    }

    public void recordJobCompleted(Job job, double currentClock) {
        totalJobsCompleted++;
        double rt = currentClock - job.getArrivalTime();
        sumResponseTime += rt;
        responseTimesSystem.add(rt);
    }

    /** Registra il tempo di sosta di una singola visita al Server B (per l'analisi dell'autocorrelazione). */
    public void recordServerBVisit(double responseTime) {
        responseTimesB.add(responseTime);
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

    public double getUtilizationA(double clock) { return busyTimeA / clock; }
    public double getUtilizationB(double clock) { return busyTimeB / clock; }
    public double getUtilizationP(double clock) { return busyTimeP / clock; }

    public double getThroughputA(double clock) { return totalComplA / clock; }
    public double getThroughputB(double clock) { return totalComplB / clock; }
    public double getThroughputP(double clock) { return totalComplP / clock; }

    public List<Double> getResponseTimesSystem() { return responseTimesSystem; }
    public List<Double> getResponseTimesB() { return responseTimesB; }

    /** Esporta una sequenza di osservazioni in un CSV a una colonna, per l'analisi (es. ACF) in Python. */
    public void exportSequenceToCsv(String path, List<Double> sequence) throws IOException {
        try (FileWriter fw = new FileWriter(path)) {
            fw.write("value\n");
            for (double v : sequence) {
                fw.write(v + "\n");
            }
        }
    }

    // Metodo fondamentale per il Warm-up (scarto del transitorio)
    public void reset() {
        areaA = 0;
        areaB = 0;
        areaP = 0;
        busyTimeA = 0;
        busyTimeB = 0;
        busyTimeP = 0;
        totalComplA = 0;
        totalComplB = 0;
        totalComplP = 0;
        totalJobsCompleted = 0;
        sumResponseTime = 0;
        responseTimesSystem.clear();
        responseTimesB.clear();
    }
}