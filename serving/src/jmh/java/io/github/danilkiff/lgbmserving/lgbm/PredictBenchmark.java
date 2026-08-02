package io.github.danilkiff.lgbmserving.lgbm;

import io.github.danilkiff.lgbmserving.data.Csv;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * Главный вопрос замера: во сколько раз режим нативного SHAP дороже обычного
 * скоринга. Ответ определяет всю топологию конвейера - стоит ли выносить
 * объяснение из горячего пути.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class PredictBenchmark {

    static final Path REFS = Path.of("refs");

    private Booster booster;
    double[][] rows;

    /** Индекс строки на поток: общий счётчик гонялся бы на параллельных бенчах. */
    @State(Scope.Thread)
    public static class Cursor {
        int i;
    }

    @Setup(Level.Trial)
    public void setup() {
        rows = holdout();
        booster = Booster.load(REFS.resolve("model.txt"));
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        booster.close();
    }

    static double[][] holdout() {
        if (!Files.isRegularFile(REFS.resolve("holdout.csv"))) {
            throw new IllegalStateException("нет эталонов - выполните `make refs`");
        }
        return Csv.read(REFS.resolve("holdout.csv"));
    }

    @Benchmark
    public double predictRaw(Cursor cursor) {
        return booster.predictRaw(rows[cursor.i++ % rows.length]);
    }

    @Benchmark
    public double[] predictContrib(Cursor cursor) {
        return booster.predictContrib(rows[cursor.i++ % rows.length]);
    }
}
