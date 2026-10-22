package com.salas.musicwave.infrastructure.outbox;

import com.salas.musicwave.application.outbox.OutboxPublisher;
import com.salas.musicwave.application.outbox.OutboxRepository;
import com.salas.musicwave.application.outbox.PublishOutboxService;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OutboxConfiguration {

    @Bean
    PublishOutboxService publishOutboxService(
            OutboxRepository repository,
            OutboxPublisher publisher,
            Clock clock,
            @Value("${musicwave.messaging.outbox-lease:PT30S}") Duration lease,
            @Value("${musicwave.messaging.publish-retry-base-delay:PT1S}") Duration retryBaseDelay,
            @Value("${musicwave.messaging.publish-retry-max-delay:PT1M}") Duration retryMaxDelay) {
        return new PublishOutboxService(
                repository, publisher, clock, lease, retryBaseDelay, retryMaxDelay);
    }
}
