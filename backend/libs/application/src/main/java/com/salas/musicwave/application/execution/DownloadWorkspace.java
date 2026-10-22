package com.salas.musicwave.application.execution;

import java.nio.file.Path;
import java.util.UUID;

public interface DownloadWorkspace {

    Path prepare(UUID jobId);

    StagedArtifact locateCompleted(UUID jobId, String reportedPath);

    void cleanupPartial(UUID jobId);
}
