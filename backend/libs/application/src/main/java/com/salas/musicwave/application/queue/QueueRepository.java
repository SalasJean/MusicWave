package com.salas.musicwave.application.queue;

import java.time.Instant;
import java.util.UUID;

public interface QueueRepository {

    QueueStartResult queuePending(UUID ownerId, Instant now);

    DeliveryAcceptance acceptDelivery(DownloadDispatchCommand command,
                                      String workerId,
                                      Instant now);

    void scheduleRetry(UUID outboxId,
                       DownloadDispatchCommand command,
                       Instant availableAt,
                       Instant now,
                       String errorCode,
                       String errorDetail);

    void failAndDeadLetter(UUID outboxId,
                           DownloadDispatchCommand command,
                           Instant now,
                           String errorCode,
                           String errorDetail);
}
