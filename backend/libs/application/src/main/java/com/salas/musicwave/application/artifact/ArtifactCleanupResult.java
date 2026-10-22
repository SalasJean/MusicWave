package com.salas.musicwave.application.artifact;

public record ArtifactCleanupResult(int claimed, int deleted, int failed) {
}
