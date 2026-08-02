package io.github.danilkiff.lgbmserving.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.danilkiff.lgbmserving.lgbm.Pool;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Ключевое свойство: насыщенная очередь explain не должна попадать на горячий
 * путь. Меряется p99 скоринга вхолостую против p99 под полной нагрузкой explain
 * и сверяется со стоимостью одного SHAP.
 *
 * <p>Тест утверждает только отсутствие синхронного SHAP; деградация от
 * конкуренции за CPU допустима и машинозависима, поэтому порог задан
 * относительно стоимости SHAP, а не относительно baseline.
 */
class HotPathIsolationTest {

    private static final int N = 2000;

    /** Порог ниже любого margin - всё отклоняется, объяснитель насыщается. */
    private static final double ALWAYS_DECLINE = -1e18;

    private static final double ALWAYS_APPROVE = 1e18;

    @Test
    void shapNeverLeaksOntoScorePath() {
        try (Pool pool = Pool.load(ScorerTest.MODEL, 8)) { // хэндлов больше, чем воркеров
            double[] row = new double[pool.numFeature()];

            // Прогрев JIT: без него первые сотни вызовов идут интерпретатором, и
            // baseline измерял бы компиляцию, а не работу.
            Scorer warmup = new Scorer(pool, ALWAYS_APPROVE, "m", null);
            for (int i = 0; i < N; i++) {
                warmup.score(row);
                pool.predictContrib(row);
            }

            long base = scoreP99(new Scorer(pool, ALWAYS_APPROVE, "m", null), row);

            ChannelQueue queue = new ChannelQueue(1024);
            MemStore store = new MemStore();
            Scorer scorer = new Scorer(pool, ALWAYS_DECLINE, "m", queue);
            Worker worker = new Worker(pool, store, WorkerConfig.of(3));
            worker.start(queue, 2);

            long loaded = scoreP99(scorer, row);
            long shap = medianContrib(pool, row);

            queue.close();
            worker.awaitDrain(java.time.Duration.ofSeconds(30));

            System.out.printf(
                    "изоляция горячего пути: p99 скоринга вхолостую %d нс, под нагрузкой %d нс | один SHAP %d нс | отклонений %d, объяснено %d%n",
                    base, loaded, shap, scorer.declined(), worker.explained());

            assertEquals(N, scorer.declined(), "не все входы отклонены");
            assertTrue(
                    loaded < shap / 2,
                    "p99 под нагрузкой %d нс приближается к одному SHAP %d нс - SHAP протёк на горячий путь"
                            .formatted(loaded, shap));
        }
    }

    private static long scoreP99(Scorer scorer, double[] row) {
        long[] samples = new long[N];
        for (int i = 0; i < N; i++) {
            long start = System.nanoTime();
            scorer.score(row);
            samples[i] = System.nanoTime() - start;
        }
        Arrays.sort(samples);
        return samples[(int) (N * 0.99)];
    }

    private static long medianContrib(Pool pool, double[] row) {
        int n = 21;
        long[] samples = new long[n];
        for (int i = 0; i < n; i++) {
            long start = System.nanoTime();
            pool.predictContrib(row);
            samples[i] = System.nanoTime() - start;
        }
        Arrays.sort(samples);
        return samples[n / 2];
    }
}
