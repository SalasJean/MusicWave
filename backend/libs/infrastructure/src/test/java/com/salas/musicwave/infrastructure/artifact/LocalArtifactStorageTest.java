package com.salas.musicwave.infrastructure.artifact;

import com.salas.musicwave.application.artifact.StoredArtifact;
import com.salas.musicwave.application.execution.StagedArtifact;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalArtifactStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesAndIdempotentlyReplacesAnOwnerIsolatedArtifact() throws Exception {
        Path root = temporaryDirectory.resolve("artifacts");
        LocalArtifactStorage storage = new LocalArtifactStorage(root);
        UUID ownerId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Path staging = Files.write(temporaryDirectory.resolve("song.mp3"), new byte[]{1, 2, 3});

        StoredArtifact first = storage.store(
                ownerId, jobId, new StagedArtifact(staging, "A \"Song\".mp3", 3));
        Files.write(staging, new byte[]{4, 5, 6, 7});
        StoredArtifact replaced = storage.store(
                ownerId, jobId, new StagedArtifact(staging, "A \"Song\".mp3", 4));

        assertEquals("owners/" + ownerId + "/jobs/" + jobId + "/artifact.mp3",
                first.storageKey());
        assertEquals(first.storageKey(), replaced.storageKey());
        assertEquals("A _Song_.mp3", replaced.filename());
        assertEquals("c6d44cf418f610e3fe9e1d9294ff43def81c6cdcad6cbb1820cff48d3aa4355d",
                replaced.sha256());
        try (var stream = storage.open(replaced).inputStream()) {
            assertArrayEquals(new byte[]{4, 5, 6, 7}, stream.readAllBytes());
        }
    }

    @Test
    void rejectsTraversalAndDeleteIsIdempotent() throws Exception {
        LocalArtifactStorage storage = new LocalArtifactStorage(temporaryDirectory.resolve("artifacts"));
        StoredArtifact traversal = new StoredArtifact(
                "../escape.mp3", "escape.mp3", "audio/mpeg", 1, "a".repeat(64));

        assertThrows(IllegalArgumentException.class, () -> storage.open(traversal));
        storage.delete("owners/owner/jobs/job/artifact.mp3");
        assertFalse(Files.exists(temporaryDirectory.resolve("escape.mp3")));
    }
}
