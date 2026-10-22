package com.salas.musicwave.worker.queue;

import com.salas.musicwave.application.download.IdentifierGenerator;
import com.salas.musicwave.application.queue.AcceptDispatchService;
import com.salas.musicwave.application.queue.HandleDispatchFailureService;
import com.salas.musicwave.application.queue.QueueRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class WorkerQueueConfiguration {

    @Bean
    IdentifierGenerator workerIdentifierGenerator() {
        return UUID::randomUUID;
    }

    @Bean
    AcceptDispatchService acceptDispatchService(QueueRepository repository, Clock clock) {
        return new AcceptDispatchService(repository, clock);
    }

    @Bean
    HandleDispatchFailureService handleDispatchFailureService(
            QueueRepository repository,
            IdentifierGenerator identifiers,
            Clock clock,
            @Value("${musicwave.messaging.max-deliveries:3}") int maxDeliveries,
            @Value("${musicwave.messaging.delivery-retry-base-delay:PT5S}") Duration retryBaseDelay) {
        return new HandleDispatchFailureService(
                repository, identifiers, clock, maxDeliveries, retryBaseDelay);
    }
}
