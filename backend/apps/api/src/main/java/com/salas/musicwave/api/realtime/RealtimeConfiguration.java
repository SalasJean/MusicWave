package com.salas.musicwave.api.realtime;

import com.salas.musicwave.application.realtime.DownloadEventStream;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RealtimeConfiguration {

    @Bean
    DownloadEventSseService downloadEventSseService(
            DownloadEventStream eventStream,
            @Value("${musicwave.realtime.read-block:PT5S}") Duration readBlock,
            @Value("${musicwave.realtime.heartbeat-interval:PT15S}") Duration heartbeatInterval,
            @Value("${musicwave.realtime.connection-timeout:PT30M}") Duration connectionTimeout,
            @Value("${musicwave.realtime.read-batch-size:100}") int readBatchSize,
            @Value("${musicwave.realtime.max-connections:100}") int maxConnections) {
        return new DownloadEventSseService(
                eventStream,
                readBlock,
                heartbeatInterval,
                connectionTimeout,
                readBatchSize,
                maxConnections);
    }
}
