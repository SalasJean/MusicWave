package com.salas.musicwave.application.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository {

    List<OutboxEvent> claimAvailable(String publisherId,
                                     Instant now,
                                     Duration leaseDuration,
                                     int limit);

    void markPublished(UUID eventId, String publisherId, Instant publishedAt);

    void recordFailure(UUID eventId,
                       String publisherId,
                       Instant nextAttemptAt,
                       String detail);
}
