package com.salas.musicwave.application.execution;

import com.salas.musicwave.application.artifact.StoredArtifact;
import com.salas.musicwave.domain.download.DownloadStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DownloadExecutionRepository {

    List<QueuedDownloadWork> claimReady(String workerId,
                                        Instant now,
                                        Duration leaseDuration,
                                        int limit,
                                        int maxPerOwner);

    void renewLeases(String workerId,
                     List<UUID> jobIds,
                     Instant now,
                     Duration leaseDuration);

    boolean markDownloading(UUID jobId, String workerId, Instant now);

    boolean checkpointProgress(UUID jobId,
                               BigDecimal progress,
                               String speed,
                               String eta,
                               Instant now);

    boolean markConverting(UUID jobId, Instant now);

    void updateTitle(UUID jobId, String title, Instant now);

    boolean cancellationRequested(UUID jobId);

    boolean markCompleted(UUID jobId,
                          StoredArtifact artifact,
                          Instant artifactExpiresAt,
                          Instant now);

    DownloadStatus markFailed(UUID jobId,
                              String errorCode,
                              String errorMessage,
                              Instant now);

    boolean markCancelled(UUID jobId, String detail, Instant now);
}
