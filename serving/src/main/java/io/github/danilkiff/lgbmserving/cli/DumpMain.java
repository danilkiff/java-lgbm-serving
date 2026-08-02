package io.github.danilkiff.lgbmserving.cli;

import io.github.danilkiff.lgbmserving.data.Csv;
import io.github.danilkiff.lgbmserving.lgbm.Booster;
import io.github.danilkiff.lgbmserving.reasoncode.ReasonCodes;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Дамп предсказаний в CSV и сверка двух дампов.
 *
 * <pre>
 * dump    &lt;model.txt&gt; &lt;holdout.csv&gt; &lt;out.csv&gt;
 * compare &lt;a.csv&gt; &lt;b.csv&gt;
 * </pre>
 *
 * <p>Дампы, снятые на разных платформах или разными реализациями с одних
 * {@code model.txt} и {@code holdout.csv}, сравнимы напрямую: любое ненулевое
 * различие - численное расхождение одной и той же модели.
 */
public final class DumpMain {

    private static final int TOP_K = 3;

    private DumpMain() {}

    public static void main(String[] argv) throws IOException {
        if (argv.length == 4 && argv[0].equals("dump")) {
            dump(Path.of(argv[1]), Path.of(argv[2]), Path.of(argv[3]));
            return;
        }
        if (argv.length == 3 && argv[0].equals("compare")) {
            compare(Path.of(argv[1]), Path.of(argv[2]));
            return;
        }
        System.err.println("usage: dump <model.txt> <holdout.csv> <out.csv>");
        System.err.println("       compare <a.csv> <b.csv>");
        System.exit(2);
    }

    private static void dump(Path modelPath, Path holdoutPath, Path outPath) throws IOException {
        double[][] rows = Csv.read(holdoutPath);
        try (Booster booster = Booster.load(modelPath);
                Writer out = new BufferedWriter(
                        Files.newBufferedWriter(outPath, StandardCharsets.UTF_8), 1 << 16)) {
            int width = booster.numFeature() + 1;
            StringBuilder header = new StringBuilder("raw");
            for (int i = 0; i < width; i++) {
                header.append(",c").append(i);
            }
            out.write(header.append('\n').toString());

            StringBuilder line = new StringBuilder(256);
            for (double[] row : rows) {
                double raw = booster.predictRaw(row);
                double[] contrib = booster.predictContrib(row);
                line.setLength(0);
                line.append(Csv.format(raw));
                for (double c : contrib) {
                    line.append(',').append(Csv.format(c));
                }
                out.write(line.append('\n').toString());
            }
            System.out.printf(
                    "wrote %s: %d rows, %d features%n", outPath, rows.length, booster.numFeature());
        }
    }

    private static void compare(Path aPath, Path bPath) {
        double[][] a = Csv.read(aPath);
        double[][] b = Csv.read(bPath);
        if (a.length != b.length || a.length == 0 || a[0].length != b[0].length) {
            System.err.printf("формы дампов не совпадают: %s против %s%n", shape(a), shape(b));
            System.exit(1);
        }
        int features = a[0].length - 2; // столбцы: raw, contributions признаков, base

        double rawMax = 0;
        double contribMax = 0;
        int flips = 0;
        int topMismatch = 0;
        for (int i = 0; i < a.length; i++) {
            rawMax = Math.max(rawMax, Math.abs(a[i][0] - b[i][0]));
            if ((a[i][0] > 0) != (b[i][0] > 0)) {
                flips++;
            }
            for (int j = 1; j < a[i].length; j++) {
                contribMax = Math.max(contribMax, Math.abs(a[i][j] - b[i][j]));
            }
            double[] ca = Arrays.copyOfRange(a[i], 1, 1 + features);
            double[] cb = Arrays.copyOfRange(b[i], 1, 1 + features);
            if (!Arrays.equals(ReasonCodes.topK(ca, TOP_K), ReasonCodes.topK(cb, TOP_K))) {
                topMismatch++;
            }
        }
        System.out.printf("строк %d, признаков %d%n", a.length, features);
        System.out.printf("raw margin: maxD=%.3e, смен решения %d%n", rawMax, flips);
        System.out.printf("contributions: maxD=%.3e%n", contribMax);
        System.out.printf("топ-%d кодов причин: расхождений %d/%d%n", TOP_K, topMismatch, a.length);
    }

    private static String shape(double[][] m) {
        return m.length == 0 ? "0x0" : m.length + "x" + m[0].length;
    }
}
