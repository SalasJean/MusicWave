package com.salas.musicwave.api.queue;

import com.salas.musicwave.application.queue.QueueRepository;
import com.salas.musicwave.application.queue.StartQueueService;
import com.salas.musicwave.application.realtime.DownloadEventPublisher;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class QueueConfiguration {

    @Bean
    StartQueueService startQueueService(QueueRepository repository,
                                        Clock clock,
                                        DownloadEventPublisher eventPublisher) {
        return new StartQueueService(repository, clock, eventPublisher);
    }
}
