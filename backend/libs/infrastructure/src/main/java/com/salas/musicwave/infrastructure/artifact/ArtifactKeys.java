package com.salas.musicwave.infrastructure.artifact;

import java.util.UUID;

final class ArtifactKeys {

    private ArtifactKeys() {
    }

    static String forJob(UUID ownerId, UUID jobId) {
        if (ownerId == null || jobId == null) {
            throw new IllegalArgumentException("ownerId and jobId are required");
        }
        return "owners/" + ownerId + "/jobs/" + jobId + "/artifact.mp3";
    }

    static String safeFilename(String value) {
        String basename = value == null ? "download.mp3" : value.replace('\\', '/');
        int separator = basename.lastIndexOf('/');
        if (separator >= 0) {
            basename = basename.substring(separator + 1);
        }
        basename = basename.replaceAll("[\\p{Cntrl}\\\";]", "_").strip();
        if (basename.isBlank()) {
            basename = "download.mp3";
        }
        if (!basename.toLowerCase(java.util.Locale.ROOT).endsWith(".mp3")) {
            basename += ".mp3";
        }
        if (basename.length() > 180) {
            basename = basename.substring(0, 176) + ".mp3";
        }
        return basename;
    }
}
