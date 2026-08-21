package model.distribution;

import libs.Rngs;

public class HyperExp implements Distribution {
    private Rngs rngs;
    private int streamIndex;
    private double cv2;

    public HyperExp(Rngs rngs, int streamIndex, double cv) {
        this.rngs = rngs;
        this.streamIndex = streamIndex;
        this.cv2 = cv * cv;
        if (cv2 <= 1.0) {
            throw new IllegalArgumentException("Il CV per l'Iperesponenziale deve essere > 1.0");
        }
    }

    @Override
    public double generate(double mean) {
        rngs.selectStream(streamIndex);

        // Calcolo dei parametri per un'Iperesponenziale
        double p1 = 0.5 * (1.0 + Math.sqrt((cv2 - 1.0) / (cv2 + 1.0)));
        double p2 = 1.0 - p1;
        double m1 = mean / (2.0 * p1);
        double m2 = mean / (2.0 * p2);

        // Generiamo due numeri casuali dallo stesso stream:
        // uno per scegliere la fase, uno per il tempo
        double u1 = rngs.random();
        double u2 = rngs.random();

        if (u1 <= p1) {
            return -m1 * Math.log(u2);
        } else {
            return -m2 * Math.log(u2);
        }
    }
}
