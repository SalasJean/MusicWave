package com.salas.musicwave.infrastructure.realtime;

import com.salas.musicwave.application.realtime.DownloadLifecycleEvent;
import com.salas.musicwave.domain.download.DownloadStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisDownloadEventPublisherTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void writesCompleteLifecycleFieldsWithExactBoundedRetention() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        StreamOperations operations = mock(StreamOperations.class);
        when(template.opsForStream()).thenReturn(operations);
        RedisDownloadEventPublisher publisher = new RedisDownloadEventPublisher(
                template, "events", 25);
        UUID ownerId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        publisher.publish(new DownloadLifecycleEvent(
                ownerId,
                jobId,
                DownloadStatus.DOWNLOADING,
                new BigDecimal("45.30"),
                "1MiB/s",
                "00:10",
                "song",
                null,
                Instant.parse("2026-10-11T12:00:00Z")));

        ArgumentCaptor<Map<String, String>> fields = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<XAddOptions> options = ArgumentCaptor.forClass(XAddOptions.class);
        verify(operations).add(eq("events"), fields.capture(), options.capture());
        assertEquals(ownerId.toString(), fields.getValue().get("ownerId"));
        assertEquals(jobId.toString(), fields.getValue().get("jobId"));
        assertEquals("DOWNLOADING", fields.getValue().get("status"));
        assertEquals("45.3", fields.getValue().get("progress"));
        assertEquals(25L, options.getValue().getMaxlen());
        assertFalse(options.getValue().isApproximateTrimming());
    }
}
