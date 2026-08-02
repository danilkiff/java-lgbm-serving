package io.github.danilkiff.lgbmserving.pipeline;

import io.github.danilkiff.lgbmserving.reasoncode.Catalog;
import java.util.function.BiConsumer;

/**
 * Настройки воркера explain.
 *
 * @param k сколько верхних кодов причин хранить на объяснение
 * @param catalog разметка признаков кодами adverse-action
 * @param deadLetter получает событие, чьё объяснение не удалось, чтобы сбои были
 *     видны, а не молча терялись
 */
public record WorkerConfig(int k, Catalog catalog, BiConsumer<DeclineEvent, RuntimeException> deadLetter) {

    public WorkerConfig {
        if (catalog == null) {
            catalog = Catalog.empty();
        }
        if (deadLetter == null) {
            deadLetter = (event, error) -> {};
        }
    }

    public static WorkerConfig of(int k) {
        return new WorkerConfig(k, null, null);
    }
}
