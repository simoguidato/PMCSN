package model.distribution;

import libs.Rngs;

public class HyperExp implements Distribution {

    private final Rngs rngs;

    // Stream per la Bernoulli che sceglie il ramo
    private final int branchStream;

    // Stream per le due esponenziali
    private final int phase1Stream;
    private final int phase2Stream;

    private final double cv2;

    public HyperExp(
            Rngs rngs,
            int branchStream,
            int phase1Stream,
            int phase2Stream,
            double cv) {

        this.rngs = rngs;
        this.branchStream = branchStream;
        this.phase1Stream = phase1Stream;
        this.phase2Stream = phase2Stream;
        this.cv2 = cv * cv;

        if (cv2 <= 1.0) {
            throw new IllegalArgumentException(
                    "Il CV per l'Iperesponenziale deve essere > 1.0"
            );
        }
    }

    @Override
    public double generate(double mean) {

        /*
         * Parametri dell'iperesponenziale H2
         */
        double p1 =
                0.5 * (1.0 + Math.sqrt((cv2 - 1.0) / (cv2 + 1.0)));

        double p2 = 1.0 - p1;

        double m1 = mean / (2.0 * p1);
        double m2 = mean / (2.0 * p2);

        /*
         * Bernoulli: scelgo il ramo.
         * Usa uno stream dedicato.
         */
        rngs.selectStream(branchStream);
        double uBranch = rngs.random();

        /*
         * Poi genero l'esponenziale utilizzando
         * lo stream associato al ramo scelto.
         */
        if (uBranch <= p1) {

            rngs.selectStream(phase1Stream);
            double u = rngs.random();

            return -m1 * Math.log(u);

        } else {

            rngs.selectStream(phase2Stream);
            double u = rngs.random();

            return -m2 * Math.log(u);
        }
    }
}