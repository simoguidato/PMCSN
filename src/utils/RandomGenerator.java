package utils;

import libs.Rngs;
import model.distribution.Distribution;
import model.distribution.Exp;
import model.distribution.HyperExp;

public class RandomGenerator {
    private Rngs rngs;

    // Assegniamo uno stream specifico per ogni processo stocastico.
    public Distribution arrivals;
    public Distribution serviceA;
    public Distribution serviceB;
    public Distribution serviceP;

    public RandomGenerator(long seed) {
        rngs = new Rngs();
        rngs.plantSeeds(seed);

        // Assegniamo gli stream e il tipo di distribuzione
        this.arrivals = new Exp(rngs, 0);
        this.serviceA = new Exp(rngs, 1);

        // SERVER B: Iperesponenziale (alta variabilità della sessione utente, CV = 2.0)
        this.serviceB = new HyperExp(rngs, 2, 2.0);

        this.serviceP = new Exp(rngs, 3);
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
}