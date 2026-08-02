package io.github.danilkiff.lgbmserving.lgbm;

/**
 * Вход неверной ширины - ошибка вызывающего, а не сбой предиктора; на этом
 * различении HTTP-слой строит 422 против 500.
 */
public final class FeatureCountException extends LgbmException {

    private static final long serialVersionUID = 1L;

    private final int expected;
    private final int got;

    public FeatureCountException(int expected, int got) {
        super("lgbm: ожидалось %d признаков, получено %d".formatted(expected, got));
        this.expected = expected;
        this.got = got;
    }

    public int expected() {
        return expected;
    }

    public int got() {
        return got;
    }
}
