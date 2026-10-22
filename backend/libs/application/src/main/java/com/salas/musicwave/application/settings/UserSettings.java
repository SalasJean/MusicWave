package com.salas.musicwave.application.settings;

import java.util.Locale;

public record UserSettings(int maxConcurrency, String audioQuality) {

    public UserSettings {
        if (maxConcurrency < 1 || maxConcurrency > 10) {
            throw new InvalidUserSettingsException(
                    "SETTINGS_MAX_CONCURRENCY_INVALID",
                    "maxConcurrency must be between 1 and 10");
        }
        if (audioQuality == null || audioQuality.isBlank()) {
            throw new InvalidUserSettingsException(
                    "SETTINGS_AUDIO_QUALITY_INVALID",
                    "audioQuality is required");
        }
        audioQuality = audioQuality.strip().toUpperCase(Locale.ROOT);
        if (!audioQuality.matches("(?:64|96|128|160|192|256|320)K")) {
            throw new InvalidUserSettingsException(
                    "SETTINGS_AUDIO_QUALITY_INVALID",
                    "audioQuality must be one of 64K, 96K, 128K, 160K, 192K, 256K or 320K");
        }
    }
}
