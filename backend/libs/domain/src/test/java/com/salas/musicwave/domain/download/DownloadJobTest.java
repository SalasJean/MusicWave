package com.salas.musicwave.domain.download;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DownloadJobTest {

    @Test
    void createsPendingJobsWithTheExpectedTitleState() {
        Instant now = Instant.parse("2026-10-10T12:00:00Z");
        DownloadJob youtube = DownloadJob.pending(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0,
                SourceUrl.parse("https://youtu.be/abc"), now);
        DownloadJob other = DownloadJob.pending(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0,
                SourceUrl.parse("https://example.com/audio"), now);

        assertEquals(DownloadStatus.PENDING, youtube.status());
        assertEquals(TitleStatus.PENDING, youtube.titleStatus());
        assertEquals(TitleStatus.NOT_APPLICABLE, other.titleStatus());
        assertEquals("https://example.com/audio", other.title());
    }
}
