package com.salas.musicwave.infrastructure.artifact;

import com.salas.musicwave.application.artifact.ArtifactMissingException;
import com.salas.musicwave.application.artifact.ArtifactStorage;
import com.salas.musicwave.application.artifact.ArtifactStorageUnavailableException;
import com.salas.musicwave.application.artifact.ArtifactStream;
import com.salas.musicwave.application.artifact.StoredArtifact;
import com.salas.musicwave.application.execution.StagedArtifact;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class LocalArtifactStorage implements ArtifactStorage {

    private static final String CONTENT_TYPE = "audio/mpeg";
    private final Path root;

    public LocalArtifactStorage(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        this.root = root.toAbsolutePath().normalize();
        if (this.root.getParent() == null) {
            throw new IllegalArgumentException("root must not be a filesystem root");
        }
    }

    @Override
    public StoredArtifact store(UUID ownerId, UUID jobId, StagedArtifact stagedArtifact) {
        String key = ArtifactKeys.forJob(ownerId, jobId);
        String filename = ArtifactKeys.safeFilename(stagedArtifact.filename());
        Path target = resolve(key);
        Path temporary = target.resolveSibling(target.getFileName() + ".upload-" + UUID.randomUUID());
        try {
            if (!Files.isRegularFile(stagedArtifact.path())
                    || Files.size(stagedArtifact.path()) != stagedArtifact.sizeBytes()) {
                throw new ArtifactMissingException("The staged artifact is no longer available");
            }
            Files.createDirectories(target.getParent());
            String sha256 = ArtifactDigests.sha256(stagedArtifact.path());
            Files.copy(stagedArtifact.path(), temporary, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredArtifact(key, filename, CONTENT_TYPE,
                    stagedArtifact.sizeBytes(), sha256);
        } catch (ArtifactMissingException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ArtifactStorageUnavailableException("Could not store the MP3 artifact", exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // A uniquely named temporary file can be cleaned independently.
            }
        }
    }

    @Override
    public ArtifactStream open(StoredArtifact artifact) {
        Path path = resolve(artifact.storageKey());
        try {
            if (!Files.isRegularFile(path)) {
                throw new ArtifactMissingException("The stored artifact is missing");
            }
            return new ArtifactStream(Files.newInputStream(path), Files.size(path));
        } catch (ArtifactMissingException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ArtifactStorageUnavailableException("Could not open the MP3 artifact", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException exception) {
            throw new ArtifactStorageUnavailableException("Could not delete the MP3 artifact", exception);
        }
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.startsWith("/")) {
            throw new IllegalArgumentException("Invalid artifact storage key");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Artifact storage key escapes its root");
        }
        return resolved;
    }
}
