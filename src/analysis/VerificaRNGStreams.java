package analysis;

import libs.Rngs;
import utils.RandomGenerator;

import java.util.Arrays;

public class VerificaRNGStreams {

    private static final long SEED = 42L;

    public static void main(String[] args) {

        verificaImplementazioneRngs();
        verificaSeedInizialiRngs();
        verificaRiproducibilitaSequenze();
        verificaAvanzamentoStream();
    }

    private static void verificaImplementazioneRngs() {

        System.out.println(
                "=== Self-test della libreria Rngs ==="
        );

        Rngs rngs = new Rngs();

        /*
         * Test già fornito nella libreria del libro.
         */
        rngs.testRandom();
    }

    private static void verificaSeedInizialiRngs() {

        /*
         * Sono i primi 6 seed ottenuti dai colleghi
         * con plantSeeds(42).
         */
        long[] expected = {
                42L,
                962850L,
                598499780L,
                334435817L,
                424484935L,
                1068730318L
        };

        Rngs rngs = new Rngs();

        rngs.plantSeeds(SEED);

        System.out.println(
                "\n=== Seed iniziali dei primi 6 stream ==="
        );

        for (int i = 0; i < expected.length; i++) {

            rngs.selectStream(i);

            long actual =
                    rngs.getSeed();

            boolean pass =
                    actual == expected[i];

            System.out.printf(
                    "stream %d: atteso=%d, ottenuto=%d -> %s%n",
                    i,
                    expected[i],
                    actual,
                    pass ? "PASS" : "FAIL"
            );

            if (!pass) {

                throw new AssertionError(
                        "Seed inatteso sullo stream " + i
                );
            }
        }
    }

    private static void verificaRiproducibilitaSequenze() {

        RandomGenerator a =
                new RandomGenerator(
                        123456789L
                );

        RandomGenerator b =
                new RandomGenerator(
                        123456789L
                );

        /*
         * Due generatori inizializzati con lo
         * stesso seed devono produrre esattamente
         * le stesse sequenze se ricevono
         * le stesse chiamate nello stesso ordine.
         */
        for (int i = 0; i < 10_000; i++) {

            assertSame(
                    a.getInterarrivalTime(1.2),
                    b.getInterarrivalTime(1.2),
                    "arrivals",
                    i
            );

            assertSame(
                    a.getServiceTimeA(0.2),
                    b.getServiceTimeA(0.2),
                    "serviceA",
                    i
            );

            assertSame(
                    a.getServiceTimeB(0.8),
                    b.getServiceTimeB(0.8),
                    "serviceB",
                    i
            );

            assertSame(
                    a.getServiceTimeP(0.4),
                    b.getServiceTimeP(0.4),
                    "serviceP",
                    i
            );
        }

        System.out.println(
                "\n=== Riproducibilita' ==="
        );

        System.out.println(
                "Stesso master seed + stesse chiamate "
                        + "-> sequenze identiche: PASS"
        );
    }

    private static void verificaAvanzamentoStream() {

        RandomGenerator rng =
                new RandomGenerator(
                        123456789L
                );

        /*
         * Stato degli stream prima della run.
         */
        long[] before =
                rng.snapshotUsedStreamSeeds();

        /*
         * Simuliamo il consumo dei vari
         * processi casuali.
         */
        for (int i = 0; i < 10_000; i++) {

            rng.getInterarrivalTime(1.2);

            rng.getServiceTimeA(0.2);

            rng.getServiceTimeB(0.8);

            rng.getServiceTimeP(0.4);
        }

        /*
         * Stato degli stessi stream dopo
         * le estrazioni.
         */
        long[] after =
                rng.snapshotUsedStreamSeeds();

        System.out.println(
                "\n=== Avanzamento degli stream ==="
        );

        System.out.println(
                "prima: "
                        + Arrays.toString(before)
        );

        System.out.println(
                "dopo : "
                        + Arrays.toString(after)
        );

        for (int i = 0;
             i < before.length;
             i++) {

            if (before[i] == after[i]) {

                throw new AssertionError(
                        "Lo stream "
                                + i
                                + " non e' avanzato"
                );
            }
        }

        System.out.println(
                "Gli stream usati avanzano "
                        + "senza essere ripiantati: PASS"
        );
    }

    private static void assertSame(
            double x,
            double y,
            String process,
            int extraction
    ) {

        /*
         * Non controlliamo semplicemente una
         * tolleranza numerica:
         *
         * vogliamo la stessa identica sequenza
         * deterministica.
         */
        if (Double.doubleToLongBits(x)
                != Double.doubleToLongBits(y)) {

            throw new AssertionError(
                    "Sequenze diverse per "
                            + process
                            + " all'estrazione "
                            + extraction
            );
        }
    }
}