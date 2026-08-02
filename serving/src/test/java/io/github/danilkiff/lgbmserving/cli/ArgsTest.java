package io.github.danilkiff.lgbmserving.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ArgsTest {

    /** Порог отрицателен в любом сценарии, где отклоняется больше половины входов. */
    @Test
    void negativeNumberIsValueNotFlag() {
        Args args = Args.parse(new String[] {"-threshold", "-1000", "-topk", "3"});
        assertEquals(-1000, args.number("threshold", 0));
        assertEquals(3, args.integer("topk", 0));
    }

    @Test
    void acceptsEqualsForm() {
        Args args = Args.parse(new String[] {"-addr=:8080", "-threshold=-0.5"});
        assertEquals(":8080", args.string("addr", ""));
        assertEquals(-0.5, args.number("threshold", 0));
    }

    @Test
    void flagWithoutValueIsTrue() {
        assertEquals("true", Args.parse(new String[] {"-verbose"}).string("verbose", ""));
    }

    @Test
    void requiresNamedFlag() {
        Args args = Args.parse(new String[0]);
        assertThrows(IllegalArgumentException.class, () -> args.require("model", "путь"));
    }

    @Test
    void rejectsPositionalArgument() {
        assertThrows(IllegalArgumentException.class, () -> Args.parse(new String[] {"model.txt"}));
    }
}
