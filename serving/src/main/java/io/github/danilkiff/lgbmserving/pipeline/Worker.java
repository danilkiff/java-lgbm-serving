package io.github.danilkiff.lgbmserving.pipeline;

import io.github.danilkiff.lgbmserving.lgbm.Pool;
import io.github.danilkiff.lgbmserving.reasoncode.Code;
import io.github.danilkiff.lgbmserving.reasoncode.ReasonCodes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Вычёрпывает {@link DeclineEvent} вне горячего пути, считает нативный SHAP
 * через пул, ранжирует топ-K кодов причин и сохраняет результат. Здесь и живёт
 * стоимость SHAP - никогда на пути скоринга.
 *
 * <p>Воркеры работают на платформенных потоках: нативный downcall занимает
 * несущий поток целиком, поэтому виртуальные ничего не дали бы, а конкурентность
 * и так ограничена размером пула хэндлов.
 */
public final class Worker {

    private final Pool pool;
    private final Store store;
    private final WorkerConfig config;
    private final AtomicLong explained = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();

    private ExecutorService executor;

    public Worker(Pool pool, Store store, WorkerConfig config) {
        this.pool = pool;
        this.store = store;
        this.config = config;
    }

    /** Запускает n воркеров, читающих очередь. */
    public void start(Queue queue, int n) {
        if (executor != null) {
            throw new IllegalStateException("explain: воркеры уже запущены");
        }
        int count = Math.max(1, n);
        executor = Executors.newFixedThreadPool(count, runnable -> {
            Thread t = new Thread(runnable);
            t.setName("explain-" + t.threadId());
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < count; i++) {
            executor.execute(() -> run(queue));
        }
        executor.shutdown();
    }

    /**
     * Блокирует до выхода всех воркеров - они выходят, когда очередь закрыта и
     * пуста. Возвращает false, если не уложились в timeout.
     */
    public boolean awaitDrain(Duration timeout) {
        if (executor == null) {
            return true;
        }
        try {
            return executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Прерывает воркеров, не дожидаясь слива очереди. */
    public void stopNow() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /** Сколько объяснений посчитано и сохранено. */
    public long explained() {
        return explained.get();
    }

    /** Сколько событий не удалось объяснить (ушли в dead-letter). */
    public long dropped() {
        return dropped.get();
    }

    private void run(Queue queue) {
        try {
            for (DeclineEvent event = queue.take(); event != null; event = queue.take()) {
                process(event);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Считает и сохраняет одно объяснение; сбой уходит в dead-letter, поэтому код
     * причины отклонения никогда не теряется молча. Повторов нет: вход и модель в
     * памяти те же, нативный сбой детерминирован.
     */
    private void process(DeclineEvent event) {
        Explanation explanation;
        try {
            explanation = explain(event);
        } catch (RuntimeException e) {
            dropped.incrementAndGet();
            config.deadLetter().accept(event, e);
            return;
        }
        store.put(explanation);
        explained.incrementAndGet();
    }

    /**
     * Contributions берутся из того же пула и тех же байт модели, что дали сам
     * margin: связь объяснения с решением обеспечена по построению, а инвариант
     * sum(contrib) == margin проверяется harness паритета. Внешний адаптер
     * очереди, приносящий события из другого процесса, обязан сверять margin сам.
     */
    private Explanation explain(DeclineEvent event) {
        double[] contrib = pool.predictContrib(event.row());
        int features = contrib.length - 1; // последний элемент - base value
        double[] featureContrib = java.util.Arrays.copyOf(contrib, features);

        int[] top = ReasonCodes.topKPositive(featureContrib, config.k());
        List<ReasonCode> reasons = new ArrayList<>(top.length);
        for (int index : top) {
            Code code = config.catalog().lookup(index);
            reasons.add(new ReasonCode(index, code.code(), code.label(), contrib[index]));
        }
        return new Explanation(
                event.id(), event.margin(), contrib[features], List.copyOf(reasons), event.modelVer());
    }
}
