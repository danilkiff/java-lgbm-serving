package io.github.danilkiff.lgbmserving.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChannelQueueTest {

    private static DeclineEvent event(String id) {
        return new DeclineEvent(id, new double[] {1}, 0.5, "m");
    }

    /** Горячий путь защищён: полная очередь отбрасывает, а не блокирует. */
    @Test
    void dropsWhenFull() {
        ChannelQueue q = new ChannelQueue(1);
        assertTrue(q.publish(event("a")));
        assertFalse(q.publish(event("b")), "второе событие обязано быть отброшено");
        assertEquals(1, q.dropped());
        assertEquals(1, q.size());
        assertEquals(1, q.capacity());
    }

    /** После закрытия потребитель дочищает остаток и получает null. */
    @Test
    void drainsThenSignalsEnd() throws Exception {
        ChannelQueue q = new ChannelQueue(4);
        q.publish(event("a"));
        q.publish(event("b"));
        q.close();

        assertEquals("a", q.take().id());
        assertEquals("b", q.take().id());
        assertNull(q.take(), "закрытая и пустая очередь обязана вернуть null");
    }

    @Test
    void rejectsPublishAfterClose() {
        ChannelQueue q = new ChannelQueue(4);
        q.close();
        assertFalse(q.publish(event("a")));
        assertEquals(1, q.dropped());
    }
}
