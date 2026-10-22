package com.salas.musicwave.application.download;

import java.util.UUID;

public record PendingTitleResolution(UUID jobId, String sourceUrl, int attempts) {
}
