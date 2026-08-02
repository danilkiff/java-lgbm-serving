package io.github.danilkiff.lgbmserving.lgbm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.danilkiff.lgbmserving.reasoncode.ReasonCodes;
import org.junit.jupiter.api.Test;

/**
 * Harness паритета: Java через FFM обязана воспроизвести числа, посчитанные
 * Python на той же модели. Библиотека одна и та же, поэтому расхождение здесь
 * означает ошибку в биндинге, а не свойство платформы.
 */
class ParityTest {

    /**
     * Допуск для одной сборки и платформы - по сути шум округления. Между
     * платформами отклонение больше, и это проверяется дампами, а не здесь.
     */
    private static final double RAW_TOL = 1e-6;

    private static final double CONTRIB_TOL = 1e-6;
    private static final double SUM_TOL = 1e-5;
    private static final int TOP_K = 3;

    @Test
    void rawMarginMatchesPython() {
        Refs.require();
        Refs.Meta meta = Refs.meta();
        try (Booster b = Booster.load(Refs.model())) {
            assertEquals(meta.nFeatures(), b.numFeature(), "число признаков разошлось с meta");

            double[][] x = Refs.matrix("holdout.csv");
            double[][] ref = Refs.matrix("ref_raw.csv");
            // Усечённый артефакт дал бы ноль сравнений и ложную зелень: формы
            // сверяются с meta до единого цикла.
            assertEquals(meta.nHoldout(), x.length, "строк в holdout не столько, сколько в meta");
            assertEquals(x.length, ref.length, "ref_raw короче holdout");

            double maxDiff = 0;
            double sumDiff = 0;
            int flips = 0;
            for (int i = 0; i < x.length; i++) {
                double got = b.predictRaw(x[i]);
                double want = ref[i][0];
                double d = Math.abs(got - want);
                if (Double.isNaN(d)) {
                    fail("строка %d: NaN в сравнении (got=%s want=%s)".formatted(i, got, want));
                }
                sumDiff += d;
                maxDiff = Math.max(maxDiff, d);
                // Решение - знак raw margin; flip это изменившееся решение.
                if ((got > 0) != (want > 0)) {
                    flips++;
                }
            }
            System.out.printf(
                    "lightgbm %s | строк %d | raw margin: maxD=%.3e meanD=%.3e | смен решения %d%n",
                    meta.lightgbmVersion(), x.length, maxDiff, sumDiff / x.length, flips);
            if (maxDiff > RAW_TOL) {
                fail("паритет raw: maxD %.3e > допуска %.0e".formatted(maxDiff, RAW_TOL));
            }
            assertEquals(0, flips, "смены решения при паритете одной сборки недопустимы");
        }
    }

    @Test
    void shapContributionsMatchPython() {
        Refs.require();
        Refs.Meta meta = Refs.meta();
        try (Booster b = Booster.load(Refs.model())) {
            double[][] x = Refs.matrix("holdout.csv");
            double[][] ref = Refs.matrix("ref_contrib.csv");
            double[][] raw = Refs.matrix("ref_raw.csv");

            int width = meta.nFeatures() + 1;
            assertEquals(meta.nHoldout(), x.length, "строк в holdout не столько, сколько в meta");
            assertEquals(x.length, ref.length, "ref_contrib короче holdout");
            assertEquals(x.length, raw.length, "ref_raw короче holdout");
            assertArrayEquals(
                    new int[] {meta.nHoldout(), width},
                    meta.contribShape(),
                    "meta.contrib_shape не отвечает числу признаков");

            double maxDiff = 0;
            double maxSumDiff = 0;
            int topMismatch = 0;
            for (int i = 0; i < x.length; i++) {
                double[] got = b.predictContrib(x[i]);
                assertEquals(width, got.length, "строка " + i + ": длина contrib");
                assertEquals(width, ref[i].length, "строка " + i + ": длина эталона contrib");

                double sum = 0;
                for (int j = 0; j < width; j++) {
                    double d = Math.abs(got[j] - ref[i][j]);
                    if (Double.isNaN(d)) {
                        fail("строка %d столбец %d: NaN в сравнении".formatted(i, j));
                    }
                    maxDiff = Math.max(maxDiff, d);
                    sum += got[j];
                }
                // Внутренний инвариант: sum(contrib) == raw margin.
                double sumDiff = Math.abs(sum - raw[i][0]);
                if (Double.isNaN(sumDiff)) {
                    fail("строка %d: NaN в sum(contrib) против raw margin".formatted(i));
                }
                maxSumDiff = Math.max(maxSumDiff, sumDiff);

                // Устойчивость кодов причин: порядок топ-K по модулю обязан
                // совпасть с эталоном, иначе объяснение назвало бы другие причины.
                double[] gotFeatures = java.util.Arrays.copyOf(got, meta.nFeatures());
                double[] refFeatures = java.util.Arrays.copyOf(ref[i], meta.nFeatures());
                if (!java.util.Arrays.equals(
                        ReasonCodes.topK(gotFeatures, TOP_K), ReasonCodes.topK(refFeatures, TOP_K))) {
                    topMismatch++;
                }
            }
            System.out.printf(
                    "contrib: maxD=%.3e | инвариант sum(contrib)=raw maxD=%.3e | расхождений топ-%d: %d/%d%n",
                    maxDiff, maxSumDiff, TOP_K, topMismatch, x.length);
            if (maxDiff > CONTRIB_TOL) {
                fail("паритет contrib: maxD %.3e > допуска %.0e".formatted(maxDiff, CONTRIB_TOL));
            }
            if (maxSumDiff > SUM_TOL) {
                fail("sum(contrib) != raw margin: maxD %.3e".formatted(maxSumDiff));
            }
            assertEquals(0, topMismatch, "порядок кодов причин разошёлся с эталоном");
        }
    }

    /**
     * Гейт версии: битовый паритет держится на том, что нативная библиотека той
     * же версии, что обучала модель. Другая версия обязана падать явно, а не
     * тихо расходиться в последних знаках.
     */
    @Test
    void nativeVersionMatchesTrainingVersion() {
        Refs.require();
        assertEquals(
                Refs.meta().lightgbmVersion(),
                NativeLibrary.version().orElseThrow(),
                "версия lib_lightgbm разошлась с версией, обучавшей модель");
    }
}
