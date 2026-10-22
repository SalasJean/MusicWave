package com.salas.musicwave.infrastructure.download;

import com.salas.musicwave.application.execution.DownloadWorkspace;
import com.salas.musicwave.application.execution.StagedArtifact;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

public final class LocalDownloadWorkspace implements DownloadWorkspace {

    private final Path root;

    public LocalDownloadWorkspace(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        Path normalized = root.toAbsolutePath().normalize();
        if (normalized.getParent() == null) {
            throw new IllegalArgumentException("root must not be a filesystem root");
        }
        this.root = normalized;
    }

    @Override
    public Path prepare(UUID jobId) {
        Path directory = directory(jobId);
        cleanupDirectory(directory);
        try {
            Files.createDirectories(directory);
            return directory;
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not create job workspace", exception);
        }
    }

    @Override
    public StagedArtifact locateCompleted(UUID jobId, String reportedPath) {
        Path directory = directory(jobId);
        try {
            Path candidate = selectCandidate(directory, reportedPath);
            Path realDirectory = directory.toRealPath();
            Path realCandidate = candidate.toRealPath();
            if (!realCandidate.startsWith(realDirectory)
                    || !Files.isRegularFile(realCandidate, LinkOption.NOFOLLOW_LINKS)
                    || !realCandidate.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3")) {
                throw new IllegalStateException("yt-dlp output is not a trusted MP3 inside the job workspace");
            }
            long size = Files.size(realCandidate);
            if (size < 1) {
                throw new IllegalStateException("yt-dlp produced an empty MP3 artifact");
            }
            removeEverythingExcept(directory, realCandidate);
            return new StagedArtifact(realCandidate, realCandidate.getFileName().toString(), size);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not validate completed MP3 artifact", exception);
        }
    }

    @Override
    public void cleanupPartial(UUID jobId) {
        cleanupDirectory(directory(jobId));
    }

    private Path selectCandidate(Path directory, String reportedPath) throws IOException {
        if (reportedPath != null && !reportedPath.isBlank()) {
            Path reported = Path.of(reportedPath).toAbsolutePath().normalize();
            if (!reported.startsWith(directory)) {
                throw new IllegalStateException("yt-dlp reported a path outside the job workspace");
            }
            if (Files.exists(reported, LinkOption.NOFOLLOW_LINKS)) {
                return reported;
            }
        }
        try (Stream<Path> files = Files.walk(directory)) {
            List<Path> mp3Files = files
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString()
                            .toLowerCase(Locale.ROOT).endsWith(".mp3"))
                    .toList();
            if (mp3Files.size() != 1) {
                throw new IllegalStateException("Expected exactly one completed MP3 artifact");
            }
            return mp3Files.get(0);
        }
    }

    private void removeEverythingExcept(Path directory, Path retainedRealPath) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (path.equals(directory)) {
                    continue;
                }
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    try (Stream<Path> children = Files.list(path)) {
                        if (children.findAny().isEmpty()) {
                            Files.deleteIfExists(path);
                        }
                    }
                } else if (!path.toRealPath().equals(retainedRealPath)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private void cleanupDirectory(Path directory) {
        ensureSafe(directory);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not clean job workspace", exception);
        }
    }

    private Path directory(UUID jobId) {
        Path directory = root.resolve(jobId.toString()).normalize();
        ensureSafe(directory);
        return directory;
    }

    private void ensureSafe(Path directory) {
        if (directory.equals(root) || !directory.startsWith(root)) {
            throw new IllegalArgumentException("Job workspace must be a child of the configured root");
        }
    }
}
