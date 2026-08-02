package io.github.danilkiff.lgbmserving.pipeline;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ограниченная очередь в процессе. Публикация неблокирующая: при полном буфере
 * событие отбрасывается и учитывается, поэтому медленный или отсутствующий
 * потребитель не добавит задержки горячему пути. Отброс виден по событию через
 * {@code publish=false} и суммарно через {@link #dropped()}.
 */
public final class ChannelQueue implements Queue {

    /**
     * Шаг ожидания потребителя. Определяет только задержку выхода воркера после
     * {@link #close()}: события забираются сразу, как только появляются.
     */
    private static final long POLL_MS = 50;

    private final BlockingQueue<DeclineEvent> queue;
    private final AtomicLong dropped = new AtomicLong();
    private volatile boolean closed;

    /**
     * Очередь с буфером на buffer событий. Нулевой буфер - передача из рук в
     * руки: событие принимается, только если потребитель уже ждёт, иначе
     * отбрасывается.
     */
    public ChannelQueue(int buffer) {
        this.queue = buffer <= 0 ? new SynchronousQueue<>() : new ArrayBlockingQueue<>(buffer);
    }

    @Override
    public boolean publish(DeclineEvent event) {
        if (closed || !queue.offer(event)) {
            dropped.incrementAndGet();
            return false;
        }
        return true;
    }

    @Override
    public DeclineEvent take() throws InterruptedException {
        while (true) {
            DeclineEvent e = queue.poll(POLL_MS, TimeUnit.MILLISECONDS);
            if (e != null) {
                return e;
            }
            if (closed) {
                return null;
            }
        }
    }

    /**
     * Закрывает очередь: издатели получают отказ, воркеры дочищают остаток и
     * выходят. Вызывать только после остановки приёма запросов.
     */
    public void close() {
        closed = true;
    }

    /** Сколько событий отброшено из-за полной или закрытой очереди. */
    public long dropped() {
        return dropped.get();
    }

    /** Глубина очереди прямо сейчас. */
    public int size() {
        return queue.size();
    }

    /** Ёмкость буфера. */
    public int capacity() {
        return queue.size() + queue.remainingCapacity();
    }
}
