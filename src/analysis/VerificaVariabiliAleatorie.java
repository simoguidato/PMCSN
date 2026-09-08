package analysis;

import libs.Rngs;
import model.distribution.Exp;
import model.distribution.HyperExp;

public class VerificaVariabiliAleatorie {

    private static final long SEED =
            42L;

    /*
     * Campione abbastanza grande da rendere
     * molto stabile la verifica.
     */
    private static final int N =
            1_000_000;

    /*
     * alpha usato per il bound di Chebyshev.
     */
    private static final double ALPHA =
            0.05;


    /*
     * Calcolo di media e varianza
     * con algoritmo di Welford.
     *
     * Evitiamo quindi di mantenere un milione
     * di double in memoria.
     */
    private static final class OnlineStats {

        long n = 0;

        double mean = 0.0;

        double m2 = 0.0;


        void add(double x) {

            n++;

            double delta =
                    x - mean;

            mean +=
                    delta / n;

            double delta2 =
                    x - mean;

            m2 +=
                    delta * delta2;
        }


        double variance() {

            return n > 1
                    ? m2 / (n - 1)
                    : Double.NaN;
        }


        double cv() {

            return Math.sqrt(
                    variance()
            ) / mean;
        }
    }


    public static void main(String[] args) {

        System.out.println(
                "=== Verifica distribuzione Esponenziale ==="
        );

        testExp(
                0.8
        );


        System.out.println(
                "\n=== Verifica distribuzione Iperesponenziale H2 ==="
        );

        testHyperExp(
                0.8,
                2.0
        );
    }


    private static void testExp(
            double theoreticalMean
    ) {

        Rngs rngs =
                new Rngs();

        rngs.plantSeeds(
                SEED
        );


        Exp exp =
                new Exp(
                        rngs,
                        0
                );


        OnlineStats stats =
                new OnlineStats();


        for (int i = 0;
             i < N;
             i++) {

            stats.add(
                    exp.generate(
                            theoreticalMean
                    )
            );
        }


        /*
         * Per Exp:
         *
         * Var[X] = E[X]^2
         */
        double theoreticalVariance =
                theoreticalMean
                        * theoreticalMean;


        printAndCheck(
                "Exp",
                theoreticalMean,
                theoreticalVariance,
                1.0,
                stats
        );
    }


    private static void testHyperExp(
            double theoreticalMean,
            double theoreticalCv
    ) {

        Rngs rngs =
                new Rngs();

        rngs.plantSeeds(
                SEED
        );


        /*
         * Usiamo esattamente gli stream
         * destinati a B nel modello:
         *
         * 2 -> Bernoulli
         * 3 -> ramo 1
         * 4 -> ramo 2
         */
        HyperExp h2 =
                new HyperExp(
                        rngs,
                        2,
                        3,
                        4,
                        theoreticalCv
                );


        OnlineStats stats =
                new OnlineStats();


        for (int i = 0;
             i < N;
             i++) {

            stats.add(
                    h2.generate(
                            theoreticalMean
                    )
            );
        }


        /*
         * Per definizione:
         *
         * CV = sigma / mean
         *
         * quindi
         *
         * Var[X] = CV^2 * mean^2
         */
        double theoreticalVariance =
                theoreticalCv
                        * theoreticalCv
                        * theoreticalMean
                        * theoreticalMean;


        printAndCheck(
                "H2",
                theoreticalMean,
                theoreticalVariance,
                theoreticalCv,
                stats
        );
    }


    private static void printAndCheck(
            String name,
            double theoreticalMean,
            double theoreticalVariance,
            double theoreticalCv,
            OnlineStats stats
    ) {

        double epsMean =
                Math.sqrt(
                        theoreticalVariance
                                / (N * ALPHA)
                );


        boolean meanPass =
                Math.abs(
                        stats.mean
                                - theoreticalMean
                ) <= epsMean;


        /*
         * Come ulteriore sanity check
         * controlliamo che la varianza
         * campionaria sia molto vicina
         * a quella teorica.
         */
        double varianceRelativeError =
                Math.abs(
                        stats.variance()
                                - theoreticalVariance
                )
                        / theoreticalVariance;


        boolean variancePass =
                varianceRelativeError
                        <= 0.02;


        System.out.printf(
                "%s - media teorica       = %.6f%n",
                name,
                theoreticalMean
        );


        System.out.printf(
                "%s - media campionaria   = %.6f%n",
                name,
                stats.mean
        );


        System.out.printf(
                "%s - epsilon Chebyshev   = %.6f%n",
                name,
                epsMean
        );


        System.out.printf(
                "%s - test media           = %s%n",
                name,
                meanPass
                        ? "PASS"
                        : "FAIL"
        );


        System.out.printf(
                "%s - varianza teorica     = %.6f%n",
                name,
                theoreticalVariance
        );


        System.out.printf(
                "%s - varianza campionaria = %.6f%n",
                name,
                stats.variance()
        );


        System.out.printf(
                "%s - errore relativo var. = %.4f%%%n",
                name,
                100.0
                        * varianceRelativeError
        );


        System.out.printf(
                "%s - test varianza        = %s%n",
                name,
                variancePass
                        ? "PASS"
                        : "FAIL"
        );


        System.out.printf(
                "%s - CV teorico           = %.6f%n",
                name,
                theoreticalCv
        );


        System.out.printf(
                "%s - CV campionario       = %.6f%n",
                name,
                stats.cv()
        );


        if (!meanPass
                || !variancePass) {

            throw new AssertionError(
                    "Verifica fallita per "
                            + name
            );
        }
    }
}
