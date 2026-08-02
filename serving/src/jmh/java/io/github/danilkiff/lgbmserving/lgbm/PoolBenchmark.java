package io.github.danilkiff.lgbmserving.lgbm;

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
import org.openjdk.jmh.annotations.Threads;

/**
 * Пропускная способность, когда все ядра берут хэндлы из пула - форма боевого
 * инференса. Множитель против однопоточного скоринга машинозависим.
 */
// Throughput, а не AverageTime: под @Threads(MAX) среднее время меряется на
// поток, и число пришлось бы делить на их количество при любом сравнении.
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Threads(Threads.MAX)
public class PoolBenchmark {

    private Pool pool;
    private double[][] rows;

    @Setup(Level.Trial)
    public void setup() {
        rows = PredictBenchmark.holdout();
        pool = Pool.load(PredictBenchmark.REFS.resolve("model.txt"),
                Runtime.getRuntime().availableProcessors());
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        pool.close();
    }

    @Benchmark
    public double poolRawParallel(PredictBenchmark.Cursor cursor) {
        return pool.predictRaw(rows[cursor.i++ % rows.length]);
    }
}
