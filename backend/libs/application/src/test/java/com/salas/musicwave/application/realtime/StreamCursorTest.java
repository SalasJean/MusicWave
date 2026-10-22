package com.salas.musicwave.application.realtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamCursorTest {

    @Test
    void parsesOrdersAndRendersRedisStreamIds() {
        StreamCursor first = StreamCursor.parse("1720000000000-4");
        StreamCursor nextSequence = StreamCursor.parse("1720000000000-5");
        StreamCursor nextMillisecond = StreamCursor.parse("1720000000001-0");

        assertEquals("1720000000000-4", first.toString());
        assertTrue(first.compareTo(nextSequence) < 0);
        assertTrue(nextSequence.compareTo(nextMillisecond) < 0);
    }

    @Test
    void rejectsMalformedOrOverflowingIds() {
        assertThrows(IllegalArgumentException.class, () -> StreamCursor.parse("latest"));
        assertThrows(IllegalArgumentException.class, () -> StreamCursor.parse("1"));
        assertThrows(IllegalArgumentException.class, () ->
                StreamCursor.parse("999999999999999999999999-0"));
    }
}
