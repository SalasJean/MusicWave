package com.salas.musicwave.application.outbox;

import java.util.Objects;
import java.util.UUID;

public record OutboxEvent(UUID id,
                          UUID aggregateId,
                          String eventType,
                          String routingKey,
                          String payload,
                          int publishAttempts,
                          String traceId,
                          String spanId) {

    public OutboxEvent(UUID id,
                       UUID aggregateId,
                       String eventType,
                       String routingKey,
                       String payload,
                       int publishAttempts) {
        this(id, aggregateId, eventType, routingKey, payload, publishAttempts, null, null);
    }

    public OutboxEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(routingKey, "routingKey");
        Objects.requireNonNull(payload, "payload");
        if (publishAttempts < 0) {
            throw new IllegalArgumentException("publishAttempts must not be negative");
        }
        if ((traceId == null) != (spanId == null)) {
            throw new IllegalArgumentException("traceId and spanId must be supplied together");
        }
        if (traceId != null && (!traceId.matches("[0-9a-fA-F]{32}")
                || !spanId.matches("[0-9a-fA-F]{16}"))) {
            throw new IllegalArgumentException("traceId or spanId has an invalid format");
        }
    }
}
