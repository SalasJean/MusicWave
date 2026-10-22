package com.salas.musicwave.application.settings;

import com.salas.musicwave.application.download.OwnerIdentity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserSettingsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final OwnerIdentity OWNER = new OwnerIdentity(UUID.randomUUID(), "settings-test");

    @Test
    void normalizesAndPersistsSupportedSettings() {
        RecordingRepository repository = new RecordingRepository();
        UserSettingsService service = new UserSettingsService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));

        UserSettings result = service.update(OWNER, 3, "256k");

        assertEquals(new UserSettings(3, "256K"), result);
        assertEquals(NOW, repository.updatedAt);
    }

    @Test
    void rejectsUnsupportedQuality() {
        UserSettingsService service = new UserSettingsService(
                new RecordingRepository(), Clock.fixed(NOW, ZoneOffset.UTC));

        InvalidUserSettingsException error = assertThrows(
                InvalidUserSettingsException.class,
                () -> service.update(OWNER, 2, "lossless"));

        assertEquals("SETTINGS_AUDIO_QUALITY_INVALID", error.code());
    }

    private static final class RecordingRepository implements UserSettingsRepository {
        private Instant updatedAt;

        @Override
        public UserSettings getOrCreate(OwnerIdentity owner, Instant now) {
            return new UserSettings(5, "192K");
        }

        @Override
        public UserSettings update(OwnerIdentity owner, UserSettings settings, Instant now) {
            updatedAt = now;
            return settings;
        }
    }
}
