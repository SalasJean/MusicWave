package com.salas.musicwave.domain.download;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadStatusTest {

    @Test
    void identifiesTerminalStatuses() {
        assertTrue(DownloadStatus.COMPLETED.isTerminal());
        assertTrue(DownloadStatus.FAILED.isTerminal());
        assertTrue(DownloadStatus.CANCELLED.isTerminal());
        assertFalse(DownloadStatus.PENDING.isTerminal());
        assertFalse(DownloadStatus.DOWNLOADING.isTerminal());
    }
}
