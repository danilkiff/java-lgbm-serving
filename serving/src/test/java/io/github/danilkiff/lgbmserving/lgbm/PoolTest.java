package io.github.danilkiff.lgbmserving.lgbm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PoolTest {

    /**
     * Долбит пул из множества потоков и сверяет каждое предсказание с
     * однопоточным эталоном. Гонка на общем состоянии предсказания проявилась бы
     * здесь несогласованными результатами для одного входа; схема "хэндл на
     * вызов" её не допускает. Прямого аналога {@code go test -race} на JVM нет,
     * поэтому доказательством служит именно это сравнение.
     */
    @Test
    void concurrentPredictionsMatchSingleThreaded() throws Exception {
        Refs.require();
        double[][] x = Refs.matrix("holdout.csv");

        double[] want = new double[x.length];
        try (Booster ref = Booster.load(Refs.model())) {
            for (int i = 0; i < x.length; i++) {
                want[i] = ref.predictRaw(x[i]);
            }
        }

        int handles = Runtime.getRuntime().availableProcessors();
        int workers = 64;
        int iters = 500;
        AtomicLong mismatches = new AtomicLong();
        AtomicLong calls = new AtomicLong();

        try (Pool pool = Pool.load(Refs.model(), handles);
                ExecutorService pool2 = Executors.newFixedThreadPool(workers)) {
            for (int w = 0; w < workers; w++) {
                int seed = w;
                pool2.execute(() -> {
                    for (int it = 0; it < iters; it++) {
                        int i = Math.floorMod(seed * 7 + it * 13, x.length);
                        double got = pool.predictRaw(x[i]);
                        calls.incrementAndGet();
                        if (got != want[i]) {
                            mismatches.incrementAndGet();
                        }
                    }
                });
            }
            pool2.shutdown();
            assertTrue(pool2.awaitTermination(5, TimeUnit.MINUTES), "воркеры не завершились");
        }

        System.out.printf(
                "пул: %d потоков x %d предсказаний = %d вызовов на %d хэндлах, расхождений %d%n",
                workers, iters, calls.get(), handles, mismatches.get());
        assertEquals(0, mismatches.get(), "предсказания разошлись под конкуренцией");
    }

    @Test
    void rejectsEmptyPool() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Pool.load(BoosterTest.FIXTURES.resolve("model.txt"), 0));
    }

    @Test
    void reportsFeatureCountErrorThroughPool() {
        try (Pool pool = Pool.load(BoosterTest.FIXTURES.resolve("model.txt"), 1)) {
            assertThrows(FeatureCountException.class, () -> pool.predictRaw(new double[] {1}));
        }
    }
}
