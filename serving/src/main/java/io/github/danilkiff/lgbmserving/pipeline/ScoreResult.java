package io.github.danilkiff.lgbmserving.pipeline;

/**
 * Ответ горячего пути по одной попытке входа. Объяснение best-effort:
 * {@code explainQueued=false} у одобрений и у отклонений, чьё событие отброшено
 * полной очередью - вызывающий узнаёт о потере сразу, а не вечным 404 на
 * запросе объяснения.
 */
public record ScoreResult(String id, double margin, Decision decision, boolean explainQueued) {}
