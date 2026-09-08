package utils;

import libs.Rngs;
import model.distribution.Distribution;
import model.distribution.Exp;
import model.distribution.HyperExp;

public class RandomGenerator {

    /*
     * Associazione fissa processo stocastico -> stream.
     */
    public static final int STREAM_ARRIVALS = 0;

    public static final int STREAM_SERVICE_A = 1;

    public static final int STREAM_B_BRANCH = 2;
    public static final int STREAM_B_PHASE_1 = 3;
    public static final int STREAM_B_PHASE_2 = 4;

    public static final int STREAM_SERVICE_P = 5;

    public static final int TOTAL_STREAMS_USED = 6;


    private final Rngs rngs;

    public final Distribution arrivals;
    public final Distribution serviceA;
    public final Distribution serviceB;
    public final Distribution serviceP;


    /*
     * Configurazione standard del nostro modello:
     * Server B iperesponenziale.
     */
    public RandomGenerator(long seed) {
        this(seed, true);
    }


    /*
     * serverBHyperExp = true:
     *      Server B iperesponenziale
     *
     * serverBHyperExp = false:
     *      Server B esponenziale
     *
     * Il secondo caso ci servirà per la validazione
     * rispetto al modello originale e per i confronti.
     */
    public RandomGenerator(long seed, boolean serverBHyperExp) {

        rngs = new Rngs();

        /*
         * Inizializza tutti gli stream a partire
         * dal master seed.
         */
        rngs.plantSeeds(seed);


        /*
         * Arrivi
         */
        arrivals =
                new Exp(rngs, STREAM_ARRIVALS);


        /*
         * Servizi Server A
         */
        serviceA =
                new Exp(rngs, STREAM_SERVICE_A);


        /*
         * Servizi Server B
         */
        if (serverBHyperExp) {

            serviceB = new HyperExp(
                    rngs,
                    STREAM_B_BRANCH,
                    STREAM_B_PHASE_1,
                    STREAM_B_PHASE_2,
                    2.0
            );

        } else {

            /*
             * Caso esponenziale.
             * Usiamo comunque uno stream dedicato a B.
             */
            serviceB =
                    new Exp(rngs, STREAM_B_PHASE_1);
        }


        /*
         * Servizi Server P
         */
        serviceP =
                new Exp(rngs, STREAM_SERVICE_P);
    }


    public double getInterarrivalTime(double lambda) {

        double mean = 1.0 / lambda;

        return arrivals.generate(mean);
    }


    public double getServiceTimeA(double meanServiceTime) {

        return serviceA.generate(meanServiceTime);
    }


    public double getServiceTimeB(double meanServiceTime) {

        return serviceB.generate(meanServiceTime);
    }


    public double getServiceTimeP(double meanServiceTime) {

        return serviceP.generate(meanServiceTime);
    }


    /*
     * Restituisce lo stato corrente dello stream.
     *
     * Importante: non genera un nuovo numero casuale.
     * Ci serve per registrare i seed all'inizio
     * di ogni replica.
     */
    public long getStreamSeed(int streamIndex) {

        rngs.selectStream(streamIndex);

        return rngs.getSeed();
    }


    /*
     * Fotografa lo stato di tutti gli stream usati.
     */
    public long[] snapshotUsedStreamSeeds() {

        long[] seeds =
                new long[TOTAL_STREAMS_USED];

        for (int i = 0; i < TOTAL_STREAMS_USED; i++) {

            seeds[i] = getStreamSeed(i);
        }

        return seeds;
    }
}