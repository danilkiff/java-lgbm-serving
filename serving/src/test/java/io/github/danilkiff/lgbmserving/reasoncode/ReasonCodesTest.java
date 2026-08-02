package io.github.danilkiff.lgbmserving.reasoncode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReasonCodesTest {

    @Test
    void topKRanksByAbsoluteValue() {
        double[] contrib = {0.1, -0.9, 0.5, -0.2};
        assertArrayEquals(new int[] {1, 2, 3}, ReasonCodes.topK(contrib, 3));
    }

    /** Ничьи разрешаются меньшим индексом: иначе порядок кодов был бы неустойчив. */
    @Test
    void topKBreaksTiesByIndex() {
        double[] contrib = {0.5, -0.5, 0.5};
        assertArrayEquals(new int[] {0, 1, 2}, ReasonCodes.topK(contrib, 3));
    }

    @Test
    void topKClampsK() {
        double[] contrib = {1, 2};
        assertEquals(0, ReasonCodes.topK(contrib, -1).length);
        assertEquals(2, ReasonCodes.topK(contrib, 99).length);
    }

    /** Нулевые и отрицательные не толкали к отклонению и причиной быть не могут. */
    @Test
    void topKPositiveDropsNonPositive() {
        double[] contrib = {0.3, -0.9, 0.0, 0.7};
        assertArrayEquals(new int[] {3, 0}, ReasonCodes.topKPositive(contrib, 3));
    }

    @Test
    void topKPositiveBreaksTiesByIndex() {
        double[] contrib = {0.4, 0.4, 0.1};
        assertArrayEquals(new int[] {0, 1}, ReasonCodes.topKPositive(contrib, 2));
    }

    @Test
    void catalogFallsBackToGenericCode() {
        Catalog catalog = Catalog.empty();
        assertEquals(new Code("R7", "feature 7"), catalog.lookup(7));
    }
}
