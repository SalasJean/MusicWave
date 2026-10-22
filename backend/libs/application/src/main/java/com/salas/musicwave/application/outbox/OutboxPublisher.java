package com.salas.musicwave.application.outbox;

public interface OutboxPublisher {

    void publish(OutboxEvent event);
}
