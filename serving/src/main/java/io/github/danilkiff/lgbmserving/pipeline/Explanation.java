package io.github.danilkiff.lgbmserving.pipeline;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Артефакт вне горячего пути для отклонённой попытки входа: топ-K кодов причин
 * плюс значения, по которым объяснение сверяется с принятым решением (margin) и
 * моделью, его принявшей (версия).
 */
public record Explanation(
        String id,
        double margin,
        double base,
        List<ReasonCode> reasons,
        @JsonProperty("model_ver") String modelVer) {}
