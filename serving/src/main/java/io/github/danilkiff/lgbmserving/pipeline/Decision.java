package io.github.danilkiff.lgbmserving.pipeline;

import com.fasterxml.jackson.annotation.JsonValue;

/** Вердикт горячего пути по одной попытке входа. */
public enum Decision {
    APPROVE("approve"),
    DECLINE("decline");

    private final String wire;

    Decision(String wire) {
        this.wire = wire;
    }

    @JsonValue
    @Override
    public String toString() {
        return wire;
    }
}
