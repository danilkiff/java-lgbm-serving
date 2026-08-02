package io.github.danilkiff.lgbmserving.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.danilkiff.lgbmserving.lgbm.FeatureCountException;
import io.github.danilkiff.lgbmserving.lgbm.Pool;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Тесты конвейера проверяют механику (решение, очередь, воркеры), а не паритет,
 * поэтому берут закоммиченную фикстуру и не требуют {@code make refs}.
 */
class ScorerTest {

    static final Path MODEL = Path.of("serving", "fixtures", "model.txt");

    /** Порог ниже любого margin - всё отклоняется. */
    private static final double ALWAYS_DECLINE = -1e18;

    /** Порог выше любого margin - всё одобряется. */
    private static final double ALWAYS_APPROVE = 1e18;

    @Test
    void declineEmitsEventWithCopyOfRow() throws Exception {
        try (Pool pool = Pool.load(MODEL, 1)) {
            double[] row = new double[pool.numFeature()];
            for (int i = 0; i < row.length; i++) {
                row[i] = i;
            }
            ChannelQueue queue = new ChannelQueue(8);
            ScoreResult res = new Scorer(pool, ALWAYS_DECLINE, "test-model", queue).score(row);

            assertEquals(Decision.DECLINE, res.decision());
            assertTrue(res.explainQueued(), "событие принято очередью");

            row[0] = -42; // вызывающий переиспользовал массив после score
            DeclineEvent event = queue.take();
            assertNotNull(event, "отклонение не выложило событие");
            assertEquals(res.id(), event.id());
            assertEquals(res.margin(), event.margin());
            assertEquals("test-model", event.modelVer());

            double[] want = new double[pool.numFeature()];
            for (int i = 0; i < want.length; i++) {
                want[i] = i;
            }
            assertArrayEquals(want, event.row(), 0.0, "событие несёт копию, а не алиас");
        }
    }

    @Test
    void approveEmitsNothing() throws Exception {
        try (Pool pool = Pool.load(MODEL, 1)) {
            ChannelQueue queue = new ChannelQueue(8);
            ScoreResult res =
                    new Scorer(pool, ALWAYS_APPROVE, "m", queue).score(new double[pool.numFeature()]);

            assertEquals(Decision.APPROVE, res.decision());
            assertFalse(res.explainQueued());
            queue.close();
            assertNull(queue.take(), "одобрение не должно выкладывать событие");
        }
    }

    /**
     * Контракт best-effort: отклонение при полной очереди остаётся успешным
     * решением, но потеря объяснения видна сразу, а не вечным 404.
     */
    @Test
    void dropIsVisibleToCaller() {
        try (Pool pool = Pool.load(MODEL, 1)) {
            ChannelQueue queue = new ChannelQueue(0); // без буфера: потребителя нет
            ScoreResult res =
                    new Scorer(pool, ALWAYS_DECLINE, "m", queue).score(new double[pool.numFeature()]);

            assertEquals(Decision.DECLINE, res.decision());
            assertFalse(res.explainQueued(), "событие отброшено");
            assertEquals(1, queue.dropped());
        }
    }

    /**
     * Порог строгий: margin, равный порогу, одобряется. Margin детерминирован на
     * одном хэндле, поэтому равенство воспроизводимо точно.
     */
    @Test
    void thresholdIsStrict() {
        try (Pool pool = Pool.load(MODEL, 1)) {
            double[] row = new double[pool.numFeature()];
            double margin = pool.predictRaw(row);

            assertEquals(
                    Decision.APPROVE,
                    new Scorer(pool, margin, "m", null).score(row).decision(),
                    "margin == порог обязан одобряться");
            assertEquals(
                    Decision.DECLINE,
                    new Scorer(pool, Math.nextDown(margin), "m", null).score(row).decision(),
                    "порог на один ulp ниже margin обязан отклонять");
        }
    }

    @Test
    void featureCountErrorReachesCaller() {
        try (Pool pool = Pool.load(MODEL, 1)) {
            Scorer scorer = new Scorer(pool, 0, "m", null);
            assertThrows(FeatureCountException.class, () -> scorer.score(new double[] {1}));
        }
    }
}
