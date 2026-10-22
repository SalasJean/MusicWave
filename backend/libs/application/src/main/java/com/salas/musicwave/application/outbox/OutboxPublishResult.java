package com.salas.musicwave.application.outbox;

public record OutboxPublishResult(int claimed, int published, int failed) {
}
