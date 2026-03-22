package utils;

import libs.Rngs;
import model.distribution.Distribution;
import model.distribution.Exp;
import model.distribution.HyperExp;

public class RandomGenerator {
    private Rngs rngs;

    // Assegniamo uno stream specifico per ogni processo stocastico.
    // Usare stream separati evita correlazioni indesiderate tra gli eventi!
    public Distribution arrivals;
    public Distribution serviceA;
    public Distribution serviceB;
    public Distribution serviceP;

    public RandomGenerator(long seed) {
        rngs = new Rngs();
        rngs.plantSeeds(seed);

        // Assegniamo gli stream e il tipo di distribuzione (Strategy Pattern)
        this.arrivals = new Exp(rngs, 0);
        this.serviceA = new Exp(rngs, 1);

        // SERVER B: Usa l'Iperesponenziale! (Esempio: Coefficiente di variazione = 2.0)
        this.serviceB = new HyperExp(rngs, 2, 2.0);

        this.serviceP = new Exp(rngs, 3);
    }

    /**
     * Genera il tempo che intercorre fino al prossimo arrivo di un utente.
     */
    public double getInterarrivalTime(double lambda) {
        double mean = 1.0 / lambda;
        // Deleghiamo il calcolo all'oggetto 'arrivals'
        return arrivals.generate(mean);
    }

    /**
     * Genera il tempo di servizio per il Server A.
     */
    public double getServiceTimeA(double meanServiceTime) {
        // Deleghiamo il calcolo all'oggetto 'serviceA'
        return serviceA.generate(meanServiceTime);
    }

    /**
     * Genera il tempo di servizio per il Server B (che ora è Iperesponenziale).
     */
    public double getServiceTimeB(double meanServiceTime) {
        return serviceB.generate(meanServiceTime);
    }

    /**
     * Genera il tempo di servizio per il Server P (Pagamenti).
     */
    public double getServiceTimeP(double meanServiceTime) {
        return serviceP.generate(meanServiceTime);
    }
}