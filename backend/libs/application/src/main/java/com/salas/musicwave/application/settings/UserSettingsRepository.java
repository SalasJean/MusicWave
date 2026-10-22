package com.salas.musicwave.application.settings;

import com.salas.musicwave.application.download.OwnerIdentity;

import java.time.Instant;

public interface UserSettingsRepository {

    UserSettings getOrCreate(OwnerIdentity owner, Instant now);

    UserSettings update(OwnerIdentity owner, UserSettings settings, Instant now);
}
