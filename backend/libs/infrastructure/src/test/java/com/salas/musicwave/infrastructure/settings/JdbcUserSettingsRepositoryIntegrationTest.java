package com.salas.musicwave.infrastructure.settings;

import com.salas.musicwave.application.download.OwnerIdentity;
import com.salas.musicwave.application.settings.UserSettings;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "MUSICWAVE_INTEGRATION_DB_URL", matches = ".+")
class JdbcUserSettingsRepositoryIntegrationTest {

    @Test
    void firstSettingsReadCreatesTheOwnerAndDefaultsOnACleanInstallation() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(System.getenv("MUSICWAVE_INTEGRATION_DB_URL"));
        dataSource.setUsername(System.getenv().getOrDefault(
                "MUSICWAVE_INTEGRATION_DB_USER", "musicwave"));
        dataSource.setPassword(System.getenv().getOrDefault(
                "MUSICWAVE_INTEGRATION_DB_PASSWORD", "musicwave"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        JdbcUserSettingsRepository repository = new JdbcUserSettingsRepository(jdbc);
        UUID ownerId = UUID.randomUUID();
        OwnerIdentity owner = new OwnerIdentity(ownerId, "settings-clean-" + ownerId);
        try {
            assertEquals(new UserSettings(5, "192K"),
                    repository.getOrCreate(owner, Instant.now()));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM app_user WHERE id = ?", Integer.class, ownerId));
            assertEquals(new UserSettings(2, "320K"), repository.update(
                    owner, new UserSettings(2, "320K"), Instant.now()));
        } finally {
            jdbc.update("DELETE FROM app_user WHERE id = ?", ownerId);
        }
    }
}
