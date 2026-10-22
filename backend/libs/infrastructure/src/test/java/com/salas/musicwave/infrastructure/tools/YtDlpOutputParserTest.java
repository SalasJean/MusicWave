package com.salas.musicwave.infrastructure.tools;

import com.salas.musicwave.application.download.DownloadOutputEvent;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YtDlpOutputParserTest {

    private final YtDlpOutputParser parser = new YtDlpOutputParser();

    @Test
    void parsesProgressSpeedAndEta() {
        List<DownloadOutputEvent> events = parser.parse(
                "[download]  45.3% of ~   5.23MiB at    1.20MiB/s ETA 00:03");

        DownloadOutputEvent.Progress progress = assertInstanceOf(
                DownloadOutputEvent.Progress.class, events.get(0));
        assertEquals(new BigDecimal("45.3"), progress.percent());
        assertEquals("1.20MiB/s", progress.speed());
        assertEquals("00:03", progress.eta());
    }

    @Test
    void parsesConversionAndDestinationFromOneLine() {
        List<DownloadOutputEvent> events = parser.parse(
                "[ExtractAudio] Destination: /downloads/My Song.mp3");

        assertInstanceOf(DownloadOutputEvent.ConversionStarted.class, events.get(0));
        DownloadOutputEvent.DestinationDetected destination = assertInstanceOf(
                DownloadOutputEvent.DestinationDetected.class, events.get(1));
        assertEquals("/downloads/My Song.mp3", destination.path());
        assertEquals("My Song", destination.title());
    }

    @Test
    void parsesErrorsAndIgnoresUnrelatedOutput() {
        DownloadOutputEvent.FailureDetected failure = assertInstanceOf(
                DownloadOutputEvent.FailureDetected.class,
                parser.parse("ERROR: [youtube] abc: Video unavailable").get(0));

        assertEquals("[youtube] abc: Video unavailable", failure.message());
        assertTrue(parser.parse("[youtube] Extracting URL metadata").isEmpty());
    }

    @Test
    void parsesStructuredProgressAndFinalArtifactMarkers() {
        DownloadOutputEvent.Progress progress = assertInstanceOf(
                DownloadOutputEvent.Progress.class,
                parser.parse("musicwave_progress: 87.5%|2.5MiB/s|00:01").get(0));
        assertEquals(new BigDecimal("87.5"), progress.percent());
        assertEquals("2.5MiB/s", progress.speed());

        DownloadOutputEvent.DestinationDetected artifact = assertInstanceOf(
                DownloadOutputEvent.DestinationDetected.class,
                parser.parse("musicwave_artifact:/safe/My Song.mp3").get(0));
        assertEquals("/safe/My Song.mp3", artifact.path());
        assertEquals("My Song", artifact.title());
    }
}
