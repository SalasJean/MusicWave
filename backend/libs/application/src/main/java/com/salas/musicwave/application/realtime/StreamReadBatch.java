package com.salas.musicwave.application.realtime;

import java.util.List;
import java.util.Objects;

public record StreamReadBatch(StreamCursor lastSeen,
                              List<StreamedDownloadEvent> events) {

    public StreamReadBatch {
        Objects.requireNonNull(lastSeen, "lastSeen");
        events = List.copyOf(events);
    }
}
