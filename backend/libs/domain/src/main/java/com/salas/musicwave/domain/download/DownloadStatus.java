package com.salas.musicwave.domain.download;

import java.util.EnumSet;
import java.util.Set;

public enum DownloadStatus {
    PENDING,
    QUEUED,
    DOWNLOADING,
    CONVERTING,
    COMPLETED,
    FAILED,
    CANCELLED;

    private static final Set<DownloadStatus> TERMINAL_STATUSES =
            EnumSet.of(COMPLETED, FAILED, CANCELLED);

    public boolean isTerminal() {
        return TERMINAL_STATUSES.contains(this);
    }
}
