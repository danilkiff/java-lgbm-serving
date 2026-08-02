package io.github.danilkiff.lgbmserving.pipeline;

import io.github.danilkiff.lgbmserving.lgbm.Pool;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Горячий путь: скоринг попытки входа, решение и - для отклонений - публикация
 * {@link DeclineEvent}. SHAP не считается инлайн никогда: он в десятки раз
 * дороже скоринга, и эта стоимость живёт в {@link Worker}.
 */
public final class Scorer {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private final Pool pool;
    private final double threshold;
    private final String modelVer;
    private final Queue queue;
    private final AtomicLong scored = new AtomicLong();
    private final AtomicLong declined = new AtomicLong();

    /**
     * Попытка входа отклоняется, когда raw margin строго превышает threshold.
     * queue может быть null - тогда отклонения ничего не выкладывают.
     */
    public Scorer(Pool pool, double threshold, String modelVer, Queue queue) {
        this.pool = pool;
        this.threshold = threshold;
        this.modelVer = modelVer;
        this.queue = queue;
    }

    /** Прогоняет строку по горячему пути; при отклонении выкладывает событие. */
    public ScoreResult score(double[] row) {
        double margin = pool.predictRaw(row);
        scored.incrementAndGet();
        String id = randomId();
        if (margin <= threshold) {
            return new ScoreResult(id, margin, Decision.APPROVE, false);
        }
        declined.incrementAndGet();
        boolean queued = queue != null
                && queue.publish(new DeclineEvent(id, row, margin, modelVer));
        return new ScoreResult(id, margin, Decision.DECLINE, queued);
    }

    /** Сколько попыток входа сосчитано. */
    public long scored() {
        return scored.get();
    }

    /** Сколько попыток входа отклонено. */
    public long declined() {
        return declined.get();
    }

    private static String randomId() {
        byte[] b = new byte[12];
        RANDOM.nextBytes(b);
        return HEX.formatHex(b);
    }
}
