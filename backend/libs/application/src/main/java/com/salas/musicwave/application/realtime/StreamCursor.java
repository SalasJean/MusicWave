package com.salas.musicwave.application.realtime;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record StreamCursor(long milliseconds, long sequence)
        implements Comparable<StreamCursor> {

    private static final Pattern FORMAT = Pattern.compile("([0-9]+)-([0-9]+)");
    public static final StreamCursor ZERO = new StreamCursor(0, 0);

    public StreamCursor {
        if (milliseconds < 0 || sequence < 0) {
            throw new IllegalArgumentException("Stream cursor components must not be negative");
        }
    }

    public static StreamCursor parse(String value) {
        Objects.requireNonNull(value, "value");
        Matcher matcher = FORMAT.matcher(value.strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid Redis Stream event ID");
        }
        try {
            return new StreamCursor(
                    Long.parseLong(matcher.group(1)),
                    Long.parseLong(matcher.group(2)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid Redis Stream event ID", exception);
        }
    }

    @Override
    public int compareTo(StreamCursor other) {
        int byMilliseconds = Long.compare(milliseconds, other.milliseconds);
        return byMilliseconds != 0 ? byMilliseconds : Long.compare(sequence, other.sequence);
    }

    @Override
    public String toString() {
        return milliseconds + "-" + sequence;
    }
}
