package io.github.danilkiff.lgbmserving.pipeline;

/**
 * Несёт {@link DeclineEvent} с горячего пути к воркеру explain. Дефолтная
 * {@link ChannelQueue} работает в процессе; внешний брокер - более поздний
 * адаптер за этим же интерфейсом.
 */
public interface Queue {

    /**
     * Никогда не блокирует горячий путь. Возвращает false, если событие
     * отброшено (очередь полна), чтобы вызывающий мог учесть потерю.
     */
    boolean publish(DeclineEvent event);

    /**
     * Сторона потребителя: следующее событие или null, если очередь закрыта и
     * пуста - воркеру пора выходить.
     */
    DeclineEvent take() throws InterruptedException;
}
