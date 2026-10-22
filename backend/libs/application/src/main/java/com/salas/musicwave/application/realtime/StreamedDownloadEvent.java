package com.salas.musicwave.application.realtime;

import java.util.Objects;

public record StreamedDownloadEvent(StreamCursor id, DownloadLifecycleEvent event) {

    public StreamedDownloadEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(event, "event");
    }
}
