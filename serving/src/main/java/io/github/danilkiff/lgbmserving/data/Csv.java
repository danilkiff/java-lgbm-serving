package io.github.danilkiff.lgbmserving.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Числовой CSV с заголовком - формат эталонов паритета и дампов предсказаний.
 *
 * <p>Один и тот же разбор нужен harness паритета и CLI дампа, поэтому живёт в
 * одном месте: расхождение в трактовке {@code nan} между ними означало бы, что
 * сравниваются разные входы.
 */
public final class Csv {

    private Csv() {}

    /** Строки файла без заголовка. */
    public static double[][] read(Path path) {
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String header = r.readLine();
            if (header == null) {
                return new double[0][];
            }
            List<double[]> rows = new ArrayList<>();
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split(",", -1);
                double[] row = new double[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    row[i] = parse(parts[i]);
                }
                rows.add(row);
            }
            return rows.toArray(new double[0][]);
        } catch (IOException e) {
            throw new UncheckedIOException("csv: не прочитать " + path, e);
        }
    }

    /**
     * Разбор одного поля. Python и Go пишут пропуск как {@code nan} строчными, а
     * {@link Double#parseDouble} принимает только {@code NaN} - без этой ветки
     * весь holdout с пропусками не читался бы.
     */
    public static double parse(String s) {
        String v = s.trim();
        return switch (v.toLowerCase(java.util.Locale.ROOT)) {
            case "nan" -> Double.NaN;
            case "inf", "+inf", "infinity", "+infinity" -> Double.POSITIVE_INFINITY;
            case "-inf", "-infinity" -> Double.NEGATIVE_INFINITY;
            default -> Double.parseDouble(v);
        };
    }

    /**
     * Запись числа для дампов: кратчайшая форма, читаемая обратно ровно в тот же
     * double. Точность дампа - предмет сверки между платформами и языками, и
     * усечение до фиксированного числа знаков внесло бы своё расхождение.
     */
    public static String format(double v) {
        return Double.toString(v);
    }
}
