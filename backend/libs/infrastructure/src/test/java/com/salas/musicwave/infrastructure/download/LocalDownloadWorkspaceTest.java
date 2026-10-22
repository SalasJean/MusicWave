package com.salas.musicwave.infrastructure.download;

import com.salas.musicwave.application.execution.StagedArtifact;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDownloadWorkspaceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void validatesOneMp3AndRemovesTemporaryFiles() throws Exception {
        LocalDownloadWorkspace workspace = new LocalDownloadWorkspace(tempDirectory.resolve("staging"));
        UUID jobId = UUID.randomUUID();
        Path directory = workspace.prepare(jobId);
        Path artifact = Files.write(directory.resolve("My Song.mp3"), new byte[]{1, 2, 3});
        Path partial = Files.write(directory.resolve("partial.webm.part"), new byte[]{9});

        StagedArtifact result = workspace.locateCompleted(jobId, artifact.toString());

        assertEquals("My Song.mp3", result.filename());
        assertEquals(3, result.sizeBytes());
        assertEquals(artifact.toRealPath(), result.path());
        assertTrue(Files.exists(artifact));
        assertFalse(Files.exists(partial));
    }

    @Test
    void rejectsAReportedPathOutsideTheJobWorkspace() throws Exception {
        LocalDownloadWorkspace workspace = new LocalDownloadWorkspace(tempDirectory.resolve("staging"));
        UUID jobId = UUID.randomUUID();
        workspace.prepare(jobId);
        Path outside = Files.write(tempDirectory.resolve("outside.mp3"), new byte[]{1});

        assertThrows(
                IllegalStateException.class,
                () -> workspace.locateCompleted(jobId, outside.toString()));
    }

    @Test
    void cleanupRemovesTheJobDirectoryOnly() throws Exception {
        LocalDownloadWorkspace workspace = new LocalDownloadWorkspace(tempDirectory.resolve("staging"));
        UUID jobId = UUID.randomUUID();
        Path directory = workspace.prepare(jobId);
        Files.write(directory.resolve("partial.tmp"), new byte[]{1});
        Path sibling = Files.write(tempDirectory.resolve("keep.txt"), new byte[]{2});

        workspace.cleanupPartial(jobId);

        assertFalse(Files.exists(directory));
        assertTrue(Files.exists(sibling));
    }
}
