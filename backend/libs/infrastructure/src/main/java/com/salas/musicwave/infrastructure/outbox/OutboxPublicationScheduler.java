package com.salas.musicwave.infrastructure.outbox;

import com.salas.musicwave.application.outbox.OutboxPublishResult;
import com.salas.musicwave.application.outbox.PublishOutboxService;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublicationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublicationScheduler.class);

    private final PublishOutboxService service;
    private final int batchSize;
    private final String publisherId;

    public OutboxPublicationScheduler(
            PublishOutboxService service,
            @Value("${musicwave.messaging.outbox-batch-size:50}") int batchSize,
            @Value("${spring.application.name:musicwave}") String applicationName) {
        this.service = service;
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException("outbox-batch-size must be between 1 and 1000");
        }
        this.batchSize = batchSize;
        this.publisherId = applicationName + "-" + UUID.randomUUID();
    }

    @Scheduled(
            initialDelayString = "${musicwave.messaging.outbox-initial-delay:PT1S}",
            fixedDelayString = "${musicwave.messaging.outbox-scan-interval:PT1S}")
    public void publish() {
        OutboxPublishResult result = service.publishAvailable(publisherId, batchSize);
        if (result.claimed() > 0) {
            LOGGER.info(
                    "Outbox batch claimed={}, published={}, failed={}",
                    result.claimed(), result.published(), result.failed());
        }
    }
}
