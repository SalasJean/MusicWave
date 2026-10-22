package com.salas.musicwave.infrastructure.realtime;

import com.salas.musicwave.application.realtime.DownloadEventPublisher;
import com.salas.musicwave.application.realtime.DownloadEventStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
public class RealtimeRedisConfiguration {

    @Bean
    DownloadEventPublisher downloadEventPublisher(
            StringRedisTemplate redisTemplate,
            @Value("${musicwave.realtime.stream-key:musicwave:download-events}") String streamKey,
            @Value("${musicwave.realtime.max-length:10000}") long maxLength) {
        return new RedisDownloadEventPublisher(redisTemplate, streamKey, maxLength);
    }

    @Bean
    DownloadEventStream downloadEventStream(
            StringRedisTemplate redisTemplate,
            @Value("${musicwave.realtime.stream-key:musicwave:download-events}") String streamKey) {
        return new RedisDownloadEventStream(redisTemplate, streamKey);
    }
}
