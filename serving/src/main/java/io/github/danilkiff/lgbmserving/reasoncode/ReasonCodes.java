package io.github.danilkiff.lgbmserving.reasoncode;

import java.util.Comparator;
import java.util.stream.IntStream;

/**
 * Ранжирование SHAP contributions строки в коды причин решения.
 *
 * <p>Работает с уже посчитанными нативным предиктором значениями и ничего не
 * пересчитывает. Хвостовой base value, который LightGBM добавляет в строку
 * SHAP, передавать сюда нельзя.
 */
public final class ReasonCodes {

    private ReasonCodes() {}

    /**
     * Индексы k contributions с наибольшим модулем, важнейший первым. Ничьи
     * разрешаются меньшим индексом ради детерминизма; k зажимается в
     * {@code [0, contrib.length]}.
     */
    public static int[] topK(double[] contrib, int k) {
        return rank(contrib, k, Comparator.comparingDouble(i -> -Math.abs(contrib[i])));
    }

    /**
     * Индексы не более k наибольших положительных contributions. Нулевые и
     * отрицательные не попадают: они не толкали к отклонению и не могут быть
     * его причиной, поэтому результат бывает короче k.
     */
    public static int[] topKPositive(double[] contrib, int k) {
        int[] positive = IntStream.range(0, contrib.length).filter(i -> contrib[i] > 0).toArray();
        return rank(positive, contrib, k, Comparator.comparingDouble(i -> -contrib[i]));
    }

    private static int[] rank(double[] contrib, int k, Comparator<Integer> order) {
        return rank(IntStream.range(0, contrib.length).toArray(), contrib, k, order);
    }

    private static int[] rank(int[] candidates, double[] contrib, int k, Comparator<Integer> order) {
        int limit = Math.clamp(k, 0, candidates.length);
        // thenComparing по индексу - тот же детерминизм ничьих, что у эталона на
        // Python: иначе равные contributions дали бы разный порядок кодов.
        return IntStream.of(candidates)
                .boxed()
                .sorted(order.thenComparing(Comparator.naturalOrder()))
                .limit(limit)
                .mapToInt(Integer::intValue)
                .toArray();
    }
}
