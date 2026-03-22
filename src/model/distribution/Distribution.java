package model.distribution;

public interface Distribution {
        /**
         * Genera un tempo casuale.
         * @param mean Il valore medio desiderato (Service Demand)
         * @return Il tempo generato
         */
        double generate(double mean);
}

