package utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

public class RngSeedLogger {

    public static void append(
            String path,
            String experiment,
            String configuration,
            int repetition,
            RandomGenerator rng)
            throws IOException {

        File file = new File(path);

        File parent = file.getParentFile();

        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        boolean writeHeader =
                !file.exists()
                        || file.length() == 0;

        long[] seeds =
                rng.snapshotUsedStreamSeeds();

        try (FileWriter fw =
                     new FileWriter(file, true)) {

            if (writeHeader) {
                fw.write(
                        "experiment,configuration,repetition,"
                                + "stream,seed\n"
                );
            }

            for (int stream = 0;
                 stream < seeds.length;
                 stream++) {

                fw.write(
                        String.format(
                                Locale.US,
                                "%s,%s,%d,%d,%d%n",
                                experiment,
                                configuration,
                                repetition,
                                stream,
                                seeds[stream]
                        )
                );
            }
        }
    }

    private RngSeedLogger() {
    }
}
