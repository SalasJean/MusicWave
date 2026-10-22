package com.salas.musicwave.infrastructure.security;

import com.salas.musicwave.application.security.OwnerRateLimiter;
import com.salas.musicwave.application.security.SourceUrlGuard;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
public class RateLimitConfiguration {

    @Bean
    SourceUrlGuard sourceUrlGuard() {
        return new DnsSourceUrlGuard();
    }

    @Bean
    OwnerRateLimiter ownerRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${musicwave.security.rate-limit.key-prefix:musicwave:rate-limit:mutations}") String keyPrefix,
            @Value("${musicwave.security.rate-limit.requests:60}") int requests,
            @Value("${musicwave.security.rate-limit.window:PT1M}") Duration window) {
        return new RedisOwnerRateLimiter(redisTemplate, keyPrefix, requests, window);
    }
}
