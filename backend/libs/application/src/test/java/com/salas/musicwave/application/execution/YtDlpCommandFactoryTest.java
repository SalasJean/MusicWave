package com.salas.musicwave.application.execution;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YtDlpCommandFactoryTest {

    @Test
    void buildsTheLegacyCompatibleMp3CommandAsAnArgumentVector() {
        YtDlpCommandFactory factory = new YtDlpCommandFactory(
                "yt-dlp", "ffmpeg", "192K");
        QueuedDownloadWork work = new QueuedDownloadWork(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "https://example.com/audio", "audio", 1);

        List<String> command = factory.create(work, Path.of("/safe/job"));

        assertEquals("yt-dlp", command.get(0));
        assertTrue(containsPair(command, "--js-runtimes", "deno"));
        assertTrue(containsPair(command, "--format", "bestaudio/best"));
        assertTrue(containsPair(command, "--audio-format", "mp3"));
        assertTrue(containsPair(command, "--audio-quality", "192K"));
        assertTrue(containsPair(command, "--paths", "/safe/job"));
        assertEquals("https://example.com/audio", command.get(command.size() - 1));
        assertFalse(command.contains("sh"));
        assertFalse(command.contains("-c"));
    }

    @Test
    void usesTheOwnersPersistedQualityForEachClaimedJob() {
        YtDlpCommandFactory factory = new YtDlpCommandFactory("yt-dlp", "ffmpeg", "192K");
        QueuedDownloadWork work = new QueuedDownloadWork(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "https://example.com/audio", "audio", 1, "320K", null, null);

        List<String> command = factory.create(work, Path.of("/safe/job"));

        assertTrue(containsPair(command, "--audio-quality", "320K"));
    }

    private boolean containsPair(List<String> values, String first, String second) {
        for (int index = 0; index < values.size() - 1; index++) {
            if (first.equals(values.get(index)) && second.equals(values.get(index + 1))) {
                return true;
            }
        }
        return false;
    }
}
