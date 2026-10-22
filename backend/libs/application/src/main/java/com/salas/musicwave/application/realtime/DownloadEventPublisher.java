package com.salas.musicwave.application.realtime;

@FunctionalInterface
public interface DownloadEventPublisher {

    void publish(DownloadLifecycleEvent event);

    static DownloadEventPublisher noop() {
        return event -> {
        };
    }
}
