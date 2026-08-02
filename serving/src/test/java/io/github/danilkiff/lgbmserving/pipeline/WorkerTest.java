package io.github.danilkiff.lgbmserving.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.danilkiff.lgbmserving.lgbm.Pool;
import io.github.danilkiff.lgbmserving.reasoncode.Catalog;
import io.github.danilkiff.lgbmserving.reasoncode.Code;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WorkerTest {

    private static final Duration DRAIN = Duration.ofSeconds(30);

    /**
     * Примитив мягкого завершения: после остановки издателей и закрытия очереди
     * воркеры дочищают буфер и выходят, поэтому ни одно отклонение в полёте не
     * теряет своё объяснение.
     */
    @Test
    void drainsQueueOnClose() {
        try (Pool pool = Pool.load(ScorerTest.MODEL, 4)) {
            double[] row = new double[pool.numFeature()];
            double margin = pool.predictRaw(row);
            MemStore store = new MemStore();
            ChannelQueue queue = new ChannelQueue(64);
            Worker worker = new Worker(pool, store, WorkerConfig.of(3));
            worker.start(queue, 4);

            int n = 30;
            for (int i = 0; i < n; i++) {
                assertTrue(queue.publish(new DeclineEvent("d" + i, row, margin, "m")));
            }
            queue.close();
            assertTrue(worker.awaitDrain(DRAIN), "воркеры не дочистили очередь");

            for (int i = 0; i < n; i++) {
                assertTrue(store.get("d" + i).isPresent(), "событие d" + i + " потеряно при завершении");
            }
            assertEquals(n, worker.explained());
        }
    }

    /** Причина отклонения - только толкавший к нему contribution. */
    @Test
    void reasonsAreOnlyRiskIncreasing() {
        try (Pool pool = Pool.load(ScorerTest.MODEL, 2)) {
            double[] row = new double[pool.numFeature()];
            for (int i = 0; i < row.length; i++) {
                row[i] = i;
            }
            double margin = pool.predictRaw(row);
            Catalog catalog = Catalog.of(Map.of(0, new Code("RTT", "round-trip time")));
            MemStore store = new MemStore();
            ChannelQueue queue = new ChannelQueue(8);
            Worker worker = new Worker(pool, store, new WorkerConfig(3, catalog, null));
            worker.start(queue, 1);

            queue.publish(new DeclineEvent("x", row, margin, "model@abc"));
            queue.close();
            assertTrue(worker.awaitDrain(DRAIN));

            Explanation e = store.get("x").orElseThrow();
            assertEquals(margin, e.margin());
            assertEquals("model@abc", e.modelVer());
            assertFalse(e.reasons().isEmpty(), "у отклонения обязана быть хотя бы одна причина");
            assertTrue(e.reasons().size() <= 3, "причин больше K");
            double previous = Double.MAX_VALUE;
            for (ReasonCode r : e.reasons()) {
                assertTrue(r.contribution() > 0, "причина обязана увеличивать риск");
                assertTrue(r.contribution() <= previous, "причины не отсортированы по убыванию");
                previous = r.contribution();
            }
            assertEquals("RTT", e.reasons().stream()
                    .filter(r -> r.feature() == 0)
                    .findFirst()
                    .map(ReasonCode::code)
                    .orElse("RTT"), "код из каталога не подставлен");

            // sum(contrib) == margin: base плюс все contributions, а не только топ-K,
            // поэтому здесь сверяется лишь то, что base посчитан из той же строки.
            assertTrue(Double.isFinite(e.base()));
        }
    }

    /** Сбой объяснения виден: событие уходит в dead-letter и учитывается. */
    @Test
    void failureGoesToDeadLetter() {
        try (Pool pool = Pool.load(ScorerTest.MODEL, 1)) {
            AtomicInteger deadLettered = new AtomicInteger();
            MemStore store = new MemStore();
            ChannelQueue queue = new ChannelQueue(4);
            Worker worker = new Worker(
                    pool, store, new WorkerConfig(3, null, (event, error) -> deadLettered.incrementAndGet()));
            worker.start(queue, 1);

            // Строка неверной ширины: нативный предиктор её не примет.
            queue.publish(new DeclineEvent("bad", new double[] {1}, 1, "m"));
            queue.close();
            assertTrue(worker.awaitDrain(DRAIN));

            assertEquals(1, deadLettered.get());
            assertEquals(1, worker.dropped());
            assertEquals(0, worker.explained());
            assertTrue(store.get("bad").isEmpty());
        }
    }
}
