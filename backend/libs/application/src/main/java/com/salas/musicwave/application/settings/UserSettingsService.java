package com.salas.musicwave.application.settings;

import com.salas.musicwave.application.download.OwnerIdentity;

import java.time.Clock;
import java.util.Objects;

public final class UserSettingsService {

    private final UserSettingsRepository repository;
    private final Clock clock;

    public UserSettingsService(UserSettingsRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public UserSettings get(OwnerIdentity owner) {
        return repository.getOrCreate(Objects.requireNonNull(owner, "owner"), clock.instant());
    }

    public UserSettings update(OwnerIdentity owner, int maxConcurrency, String audioQuality) {
        UserSettings settings = new UserSettings(maxConcurrency, audioQuality);
        return repository.update(Objects.requireNonNull(owner, "owner"), settings, clock.instant());
    }
}
