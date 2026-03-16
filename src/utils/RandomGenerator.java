package utils;

import libs.Rngs;

public class RandomGenerator {
    private Rngs rngs;

    // Assegniamo uno stream specifico per ogni processo stocastico.
    // Usare stream separati evita correlazioni indesiderate tra gli eventi!
    private final int STREAM_ARRIVALS = 0;
    private final int STREAM_SERVICE_A = 1;
    private final int STREAM_SERVICE_B = 2;
    private final int STREAM_SERVICE_P = 3;

    public RandomGenerator(long seed) {
        rngs = new Rngs();
        // plantSeeds inizializza il flusso di default e tutti gli altri flussi
        // automaticamente con valori dipendenti dal seed fornito
        rngs.plantSeeds(seed);
    }

    /**
     * Genera il tempo che intercorre fino al prossimo arrivo di un utente.
     */
    public double getInterarrivalTime(double lambda) {
        rngs.selectStream(STREAM_ARRIVALS);
        double mean = 1.0 / lambda;
        // Inversione della CDF esponenziale
        return -mean * Math.log(rngs.random());
    }

    /**
     * Genera il tempo di servizio per il Server A.
     */
    public double getServiceTimeA(double meanServiceTime) {
        rngs.selectStream(STREAM_SERVICE_A);
        return -meanServiceTime * Math.log(rngs.random());
    }

    /**
     * Genera il tempo di servizio per il Server B.
     */
    public double getServiceTimeB(double meanServiceTime) {
        rngs.selectStream(STREAM_SERVICE_B);
        return -meanServiceTime * Math.log(rngs.random());
    }

    /**
     * Genera il tempo di servizio per il Server P (Pagamenti).
     */
    public double getServiceTimeP(double meanServiceTime) {
        rngs.selectStream(STREAM_SERVICE_P);
        return -meanServiceTime * Math.log(rngs.random());
    }
}