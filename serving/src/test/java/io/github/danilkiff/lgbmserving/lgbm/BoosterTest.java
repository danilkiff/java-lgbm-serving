package io.github.danilkiff.lgbmserving.lgbm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BoosterTest {

    static final Path FIXTURES = Path.of("serving", "fixtures");

    @Test
    void loadsFixtureAndScores() {
        try (Booster b = Booster.load(FIXTURES.resolve("model.txt"))) {
            assertTrue(b.numFeature() > 0, "модель без признаков");

            double[] row = new double[b.numFeature()];
            double margin = b.predictRaw(row);
            double[] contrib = b.predictContrib(row);

            assertEquals(b.numFeature() + 1, contrib.length, "длина contrib - признаки плюс base");
            double sum = 0;
            for (double c : contrib) {
                sum += c;
            }
            assertEquals(margin, sum, 1e-9, "sum(contrib) обязан сойтись с raw margin");
        }
    }

    /** Буферы обмена переиспользуются - повторный вызов обязан давать то же число. */
    @Test
    void repeatedCallsAreStable() {
        try (Booster b = Booster.load(FIXTURES.resolve("model.txt"))) {
            double[] row = new double[b.numFeature()];
            for (int i = 0; i < row.length; i++) {
                row[i] = i;
            }
            double first = b.predictRaw(row);
            b.predictContrib(row);
            assertEquals(first, b.predictRaw(row), 0.0, "предсказание уплыло между вызовами");
        }
    }

    @Test
    void rejectsWrongFeatureCount() {
        try (Booster b = Booster.load(FIXTURES.resolve("model.txt"))) {
            FeatureCountException e =
                    assertThrows(FeatureCountException.class, () -> b.predictRaw(new double[]{1}));
            assertEquals(b.numFeature(), e.expected());
            assertEquals(1, e.got());
        }
    }

    /**
     * Мультиклассовая модель отдаёт несколько значений на строку: раз raw-путь
     * читает только первое, отказ обязан случиться на загрузке.
     */
    @Test
    void rejectsMulticlassOnLoad() {
        LgbmException e = assertThrows(
                LgbmException.class, () -> Booster.load(FIXTURES.resolve("multiclass.txt")));
        assertTrue(e.getMessage().contains("на строку"), e.getMessage());
    }

    @Test
    void nativeVersionIsKnown() {
        assertTrue(NativeLibrary.version().isPresent(), "нет VERSION.txt рядом с библиотекой");
    }
}
