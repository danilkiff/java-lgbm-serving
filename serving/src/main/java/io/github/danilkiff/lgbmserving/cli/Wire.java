package io.github.danilkiff.lgbmserving.cli;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.danilkiff.lgbmserving.pipeline.Decision;
import java.util.List;

/** Контракт REST-слоя. Имена полей повторяют Go-реализацию сервиса. */
final class Wire {

    private Wire() {}

    /**
     * Признаки объектами, а не примитивами, ради null: JSON не умеет NaN, а
     * пропуск (непомеренный RTT - штатный случай) идёт в missing-ветки деревьев.
     * Молчаливый ноль был бы другим, легитимным значением.
     */
    record ScoreRequest(List<Double> features) {}

    /**
     * {@code explainQueued=true} только у отклонения, чьё событие принято
     * очередью: запрос объяснения по этому id со временем ответит.
     */
    record ScoreResponse(
            String id,
            double margin,
            Decision decision,
            @JsonProperty("explain_queued") boolean explainQueued) {}

    /** Операционный снимок: горячий путь, очередь explain, прогресс объяснителя. */
    record Metrics(
            long scored,
            long declined,
            @JsonProperty("decline_rate") double declineRate,
            @JsonProperty("queue_len") int queueLen,
            @JsonProperty("queue_cap") int queueCap,
            @JsonProperty("queue_dropped") long queueDropped,
            long explained,
            @JsonProperty("dead_lettered") long deadLettered) {}
}
