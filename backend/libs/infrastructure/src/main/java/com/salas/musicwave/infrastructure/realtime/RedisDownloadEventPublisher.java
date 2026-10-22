package com.salas.musicwave.infrastructure.realtime;

import com.salas.musicwave.application.realtime.DownloadEventPublisher;
import com.salas.musicwave.application.realtime.DownloadLifecycleEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

public final class RedisDownloadEventPublisher implements DownloadEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisDownloadEventPublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final String streamKey;
    private final long maxLength;

    public RedisDownloadEventPublisher(StringRedisTemplate redisTemplate,
                                       String streamKey,
                                       long maxLength) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        if (streamKey == null || streamKey.isBlank()) {
            throw new IllegalArgumentException("streamKey must not be blank");
        }
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be positive");
        }
        this.streamKey = streamKey;
        this.maxLength = maxLength;
    }

    @Override
    public void publish(DownloadLifecycleEvent event) {
        Objects.requireNonNull(event, "event");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("ownerId", event.ownerId().toString());
        fields.put("jobId", event.jobId().toString());
        fields.put("status", event.status().name());
        fields.put("progress", event.progress().stripTrailingZeros().toPlainString());
        fields.put("speed", empty(event.speed()));
        fields.put("eta", empty(event.eta()));
        fields.put("title", empty(event.title()));
        fields.put("errorCode", empty(event.errorCode()));
        fields.put("occurredAt", event.occurredAt().toString());
        try {
            redisTemplate.opsForStream().add(
                    streamKey,
                    fields,
                    XAddOptions.maxlen(maxLength).approximateTrimming(false));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not publish ephemeral download event jobId={} status={}",
                    event.jobId(),
                    event.status(),
                    exception);
            throw exception;
        }
    }

    private String empty(String value) {
        return value == null ? "" : value;
    }
}
