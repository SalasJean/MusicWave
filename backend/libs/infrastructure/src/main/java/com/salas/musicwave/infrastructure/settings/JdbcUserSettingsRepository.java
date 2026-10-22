package com.salas.musicwave.infrastructure.settings;

import com.salas.musicwave.application.settings.UserSettings;
import com.salas.musicwave.application.settings.UserSettingsRepository;
import com.salas.musicwave.application.download.OwnerIdentity;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcUserSettingsRepository implements UserSettingsRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserSettingsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public UserSettings getOrCreate(OwnerIdentity owner, Instant now) {
        ensureOwner(owner, now);
        jdbcTemplate.update(
                """
                INSERT INTO user_settings (user_id, created_at, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT (user_id) DO NOTHING
                """,
                owner.id(), Timestamp.from(now), Timestamp.from(now));
        return find(owner.id());
    }

    @Override
    @Transactional
    public UserSettings update(OwnerIdentity owner, UserSettings settings, Instant now) {
        ensureOwner(owner, now);
        jdbcTemplate.update(
                """
                INSERT INTO user_settings (
                    user_id, max_concurrency, audio_quality, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE
                SET max_concurrency = EXCLUDED.max_concurrency,
                    audio_quality = EXCLUDED.audio_quality,
                    updated_at = EXCLUDED.updated_at
                """,
                owner.id(),
                settings.maxConcurrency(),
                settings.audioQuality(),
                Timestamp.from(now),
                Timestamp.from(now));
        return find(owner.id());
    }

    private void ensureOwner(OwnerIdentity owner, Instant now) {
        jdbcTemplate.update(
                """
                INSERT INTO app_user (id, external_subject, created_at)
                VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """,
                owner.id(), owner.externalSubject(), Timestamp.from(now));
    }

    private UserSettings find(UUID ownerId) {
        return jdbcTemplate.queryForObject(
                "SELECT max_concurrency, audio_quality FROM user_settings WHERE user_id = ?",
                (resultSet, rowNumber) -> new UserSettings(
                        resultSet.getInt("max_concurrency"),
                        resultSet.getString("audio_quality")),
                ownerId);
    }
}
