package io.github.danilkiff.lgbmserving.lgbm;

/**
 * Сбой нативного предиктора. Отличается от {@link FeatureCountException}: там
 * виноват вход вызывающего, здесь - сама библиотека, и для HTTP это 500.
 */
public class LgbmException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LgbmException(String message) {
        super(message);
    }

    public LgbmException(String message, Throwable cause) {
        super(message, cause);
    }
}
