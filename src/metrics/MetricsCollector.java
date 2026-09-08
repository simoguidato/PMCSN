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

    private double areaA = 0.0;
    private double areaB = 0.0;
    private double areaP = 0.0;

    private double busyTimeA = 0.0;
    private double busyTimeB = 0.0;
    private double busyTimeP = 0.0;

    private long totalComplA = 0;
    private long totalComplB = 0;
    private long totalComplP = 0;

    /*
     * Numero totale di job completati dopo
     * l'ultimo reset delle metriche.
     *
     * Comprende anche eventuali job che erano
     * già presenti nel sistema al confine
     * del warm-up.
     */
    private long totalJobsCompleted = 0;


    /*
     * Statistiche del Response Time globale.
     */
    private double sumResponseTime = 0.0;

    /*
     * Numero di response time effettivamente
     * ammessi nella fase di misura.
     */
    private long totalResponseSamples = 0;


    /*
     * Un job contribuisce alle statistiche di R
     * soltanto se il suo arrivo esterno è
     * successivo a questo istante.
     *
     * Di default -infinito:
     * nelle normali simulazioni tutti i job
     * vengono considerati.
     */
    private double responseCollectionStartTime =
            Double.NEGATIVE_INFINITY;


    private final List<Double> responseTimesSystem =
            new ArrayList<>();

    private final List<Double> responseTimesB =
            new ArrayList<>();


    private TimeBatchCollector timeBatchCollector =
            null;

    private TransientSampler transientSampler =
            null;


    /*
     * Tracciamento job-by-job.
     */
    private BufferedWriter traceWriter;

    private boolean isTracingEnabled =
            false;


    /*
     * =========================================================
     * JOB TRACING
     * =========================================================
     */

    public void enableJobTracing(
            String csvFilePath) {

        try {

            traceWriter =
                    new BufferedWriter(
                            new FileWriter(
                                    csvFilePath
                            )
                    );


            traceWriter.write(
                    "OrigID,Arrival0,Completion,TempoRispostaTotal,"
                            + "Server_1,IN_1,OUT_1,RT_1,"
                            + "Server_2,IN_2,OUT_2,RT_2,"
                            + "Server_3,IN_3,OUT_3,RT_3,"
                            + "Server_4,IN_4,OUT_4,RT_4,"
                            + "Server_5,IN_5,OUT_5,RT_5\n"
            );


            isTracingEnabled =
                    true;


        } catch (IOException e) {

            e.printStackTrace();
        }
    }


    public void closeTracing() {

        if (isTracingEnabled
                && traceWriter != null) {

            try {

                traceWriter.close();

            } catch (IOException e) {

                e.printStackTrace();
            }
        }
    }


    /*
     * =========================================================
     * TIME BATCHING
     * =========================================================
     */

    public void enableTimeBatching(
            double deltaT) {

        this.timeBatchCollector =
                new TimeBatchCollector(
                        deltaT
                );
    }


    public void enableTimeBatching(
            double deltaT,
            double startTime) {

        this.timeBatchCollector =
                new TimeBatchCollector(
                        deltaT,
                        startTime
                );
    }


    public TimeBatchCollector
    getTimeBatchCollector() {

        return timeBatchCollector;
    }


    /*
     * =========================================================
     * TRANSIENT SAMPLING
     * =========================================================
     */

    public void enableTransientSampling(
            double sampleInterval) {

        this.transientSampler =
                new TransientSampler(
                        sampleInterval
                );
    }


    public TransientSampler
    getTransientSampler() {

        return transientSampler;
    }


    /*
     * =========================================================
     * CONFINE DEL WARM-UP PER R
     * =========================================================
     */

    public void setResponseCollectionStartTime(
            double startTime) {

        this.responseCollectionStartTime =
                startTime;
    }


    /*
     * =========================================================
     * INTEGRAZIONE DELLE AREE
     * =========================================================
     */

    public void updateAreas(
            double startTs,
            double endTs,
            int sizeA,
            int sizeB,
            int sizeP) {


        double deltaT =
                endTs - startTs;


        areaA +=
                sizeA * deltaT;

        areaB +=
                sizeB * deltaT;

        areaP +=
                sizeP * deltaT;


        if (sizeA > 0) {

            busyTimeA +=
                    deltaT;
        }


        if (sizeB > 0) {

            busyTimeB +=
                    deltaT;
        }


        if (sizeP > 0) {

            busyTimeP +=
                    deltaT;
        }


        if (timeBatchCollector != null) {

            timeBatchCollector.advance(
                    startTs,
                    endTs,
                    sizeA,
                    sizeB,
                    sizeP
            );
        }


        if (transientSampler != null) {

            transientSampler.advance(
                    startTs,
                    endTs,
                    sizeA,
                    sizeB,
                    sizeP
            );
        }
    }


    /*
     * =========================================================
     * DEPARTURE DA UNA STAZIONE
     * =========================================================
     */

    public void recordDeparture(
            ServerId server,
            double eventTime) {


        switch (server) {

            case SERVER_A:

                totalComplA++;

                break;


            case SERVER_B:

                totalComplB++;

                break;


            case SERVER_P:

                totalComplP++;

                break;
        }


        if (timeBatchCollector != null) {

            timeBatchCollector.recordDeparture(
                    server,
                    eventTime
            );
        }
    }


    /*
     * =========================================================
     * COMPLETAMENTO END-TO-END
     * =========================================================
     */

    public void recordJobCompleted(
            Job job,
            double currentClock) {


        /*
         * Questo contatore serve anche
         * a SimulationEngine.run().
         *
         * Quindi conta qualsiasi completamento
         * avvenuto dopo il reset.
         */
        totalJobsCompleted++;


        double rt =
                currentClock
                        - job.getArrivalTime();


        /*
         * Per il Response Time stazionario
         * ammettiamo soltanto i job il cui
         * ARRIVO ESTERNO è avvenuto dopo
         * l'inizio della fase di misura.
         *
         * Un job già presente nel sistema
         * a t = WARMUP_TIME viene quindi
         * escluso da R.
         */
        if (job.getArrivalTime()
                >= responseCollectionStartTime) {


            sumResponseTime +=
                    rt;


            responseTimesSystem.add(
                    rt
            );


            totalResponseSamples++;
        }


        /*
         * Il tracing rimane indipendente
         * dalla selezione statistica di R.
         */
        if (isTracingEnabled) {

            try {

                StringBuilder sb =
                        new StringBuilder();


                sb.append(
                        String.format(
                                Locale.US,
                                "%d,%.6f,%.6f,%.6f",
                                job.getId(),
                                job.getArrivalTime(),
                                currentClock,
                                rt
                        )
                );


                for (Job.VisitRecord v :
                        job.getVisits()) {


                    sb.append(
                            String.format(
                                    Locale.US,
                                    ",%s,%.6f,%.6f,%.6f",
                                    v.serverName,
                                    v.inTime,
                                    v.outTime,
                                    v.responseTime
                            )
                    );
                }


                int missingVisits =
                        5
                                - job
                                .getVisits()
                                .size();


                for (int i = 0;
                     i < missingVisits;
                     i++) {

                    sb.append(
                            ",,,,"
                    );
                }


                sb.append(
                        "\n"
                );


                traceWriter.write(
                        sb.toString()
                );


            } catch (IOException e) {

                e.printStackTrace();
            }
        }
    }


    /*
     * =========================================================
     * RESPONSE TIME DEL SERVER B
     * =========================================================
     */

    public void recordServerBVisit(
            double responseTime) {

        responseTimesB.add(
                responseTime
        );
    }


    /*
     * =========================================================
     * GETTER
     * =========================================================
     */

    public long getTotalJobsCompleted() {

        return totalJobsCompleted;
    }


    public long getTotalResponseSamples() {

        return totalResponseSamples;
    }


    public double getAverageResponseTime() {

        return totalResponseSamples == 0
                ? 0.0
                : sumResponseTime
                / totalResponseSamples;
    }


    public double getAverageJobsInSystemA(
            double clock) {

        return areaA / clock;
    }


    public double getAverageJobsInSystemB(
            double clock) {

        return areaB / clock;
    }


    public double getAverageJobsInSystemP(
            double clock) {

        return areaP / clock;
    }


    public double getUtilizationA(
            double clock) {

        return busyTimeA / clock;
    }


    public double getUtilizationB(
            double clock) {

        return busyTimeB / clock;
    }


    public double getUtilizationP(
            double clock) {

        return busyTimeP / clock;
    }


    public double getThroughputA(
            double clock) {

        return totalComplA / clock;
    }


    public double getThroughputB(
            double clock) {

        return totalComplB / clock;
    }


    public double getThroughputP(
            double clock) {

        return totalComplP / clock;
    }


    public List<Double>
    getResponseTimesSystem() {

        return responseTimesSystem;
    }


    public List<Double>
    getResponseTimesB() {

        return responseTimesB;
    }


    public void exportSequenceToCsv(
            String path,
            List<Double> sequence)
            throws IOException {


        try (FileWriter fw =
                     new FileWriter(path)) {


            fw.write(
                    "value\n"
            );


            for (double v :
                    sequence) {

                fw.write(
                        v + "\n"
                );
            }
        }
    }


    /*
     * =========================================================
     * RESET DELLE STATISTICHE
     * =========================================================
     */

    public void reset() {


        areaA =
                0.0;

        areaB =
                0.0;

        areaP =
                0.0;


        busyTimeA =
                0.0;

        busyTimeB =
                0.0;

        busyTimeP =
                0.0;


        totalComplA =
                0;

        totalComplB =
                0;

        totalComplP =
                0;


        totalJobsCompleted =
                0;


        totalResponseSamples =
                0;


        sumResponseTime =
                0.0;


        responseTimesSystem.clear();

        responseTimesB.clear();
    }
}