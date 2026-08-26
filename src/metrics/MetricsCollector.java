package metrics;

import model.Job;
import model.ServerId;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MetricsCollector {
    // ... [Variabili esistenti mantenute inalterate] ...
    private double areaA = 0.0, areaB = 0.0, areaP = 0.0;
    private double busyTimeA = 0.0, busyTimeB = 0.0, busyTimeP = 0.0;
    private long totalComplA = 0, totalComplB = 0, totalComplP = 0;
    private long totalJobsCompleted = 0;
    private double sumResponseTime = 0.0;

    private final List<Double> responseTimesSystem = new ArrayList<>();
    private final List<Double> responseTimesB = new ArrayList<>();
    private TimeBatchCollector timeBatchCollector = null;
    private TransientSampler transientSampler = null;

    // [NUOVO] Variabili per il Tracciamento Job-by-Job
    private BufferedWriter traceWriter;
    private boolean isTracingEnabled = false;

    // [NUOVO] Abilita e chiudi il logging
    public void enableJobTracing(String csvFilePath) {
        try {
            traceWriter = new BufferedWriter(new FileWriter(csvFilePath));
            traceWriter.write("OrigID,Arrival0,Completion,TempoRispostaTotal," +
                    "Server_1,IN_1,OUT_1,RT_1," +
                    "Server_2,IN_2,OUT_2,RT_2," +
                    "Server_3,IN_3,OUT_3,RT_3," +
                    "Server_4,IN_4,OUT_4,RT_4," +
                    "Server_5,IN_5,OUT_5,RT_5\n");
            isTracingEnabled = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void closeTracing() {
        if (isTracingEnabled && traceWriter != null) {
            try {
                traceWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // ... [Metodi enableTimeBatching, getTransientSampler, updateAreas, recordDeparture inalterati] ...
    public void enableTimeBatching(double deltaT) { this.timeBatchCollector = new TimeBatchCollector(deltaT); }
    public void enableTimeBatching(double deltaT, double startTime) { this.timeBatchCollector = new TimeBatchCollector(deltaT, startTime); }
    public TimeBatchCollector getTimeBatchCollector() { return timeBatchCollector; }
    public void enableTransientSampling(double sampleInterval) { this.transientSampler = new TransientSampler(sampleInterval); }
    public TransientSampler getTransientSampler() { return transientSampler; }

    public void updateAreas(double startTs, double endTs, int sizeA, int sizeB, int sizeP) {
        double deltaT = endTs - startTs;
        areaA += sizeA * deltaT; areaB += sizeB * deltaT; areaP += sizeP * deltaT;
        if (sizeA > 0) busyTimeA += deltaT;
        if (sizeB > 0) busyTimeB += deltaT;
        if (sizeP > 0) busyTimeP += deltaT;
        if (timeBatchCollector != null) timeBatchCollector.advance(startTs, endTs, sizeA, sizeB, sizeP);
        if (transientSampler != null) transientSampler.advance(startTs, endTs, sizeA, sizeB, sizeP);
    }

    public void recordDeparture(ServerId server, double eventTime) {
        switch (server) {
            case SERVER_A: totalComplA++; break;
            case SERVER_B: totalComplB++; break;
            case SERVER_P: totalComplP++; break;
        }
        if (timeBatchCollector != null) timeBatchCollector.recordDeparture(server, eventTime);
    }

    public void recordJobCompleted(Job job, double currentClock) {
        totalJobsCompleted++;
        double rt = currentClock - job.getArrivalTime();
        sumResponseTime += rt;
        responseTimesSystem.add(rt);

        // [NUOVO] Salvataggio istantaneo della cronologia su CSV
        if (isTracingEnabled) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format(Locale.US, "%d,%.6f,%.6f,%.6f",
                        job.getId(), job.getArrivalTime(), currentClock, rt));

                for (Job.VisitRecord v : job.getVisits()) {
                    sb.append(String.format(Locale.US, ",%s,%.6f,%.6f,%.6f",
                            v.serverName, v.inTime, v.outTime, v.responseTime));
                }

                // Padding con virgole vuote se ha fatto meno di 5 visite
                int missingVisits = 5 - job.getVisits().size();
                for (int i = 0; i < missingVisits; i++) {
                    sb.append(",,,,");
                }
                sb.append("\n");
                traceWriter.write(sb.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void recordServerBVisit(double responseTime) { responseTimesB.add(responseTime); }

    // ... [Tutti i getter esistenti (AverageResponseTime, Utilization, ecc.) inalterati] ...
    public long getTotalJobsCompleted() { return totalJobsCompleted; }
    public double getAverageResponseTime() { return totalJobsCompleted == 0 ? 0.0 : sumResponseTime / totalJobsCompleted; }
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

    public void exportSequenceToCsv(String path, List<Double> sequence) throws IOException {
        try (FileWriter fw = new FileWriter(path)) {
            fw.write("value\n");
            for (double v : sequence) fw.write(v + "\n");
        }
    }

    public void reset() {
        areaA = 0; areaB = 0; areaP = 0;
        busyTimeA = 0; busyTimeB = 0; busyTimeP = 0;
        totalComplA = 0; totalComplB = 0; totalComplP = 0;
        totalJobsCompleted = 0; sumResponseTime = 0;
        responseTimesSystem.clear(); responseTimesB.clear();
    }
}