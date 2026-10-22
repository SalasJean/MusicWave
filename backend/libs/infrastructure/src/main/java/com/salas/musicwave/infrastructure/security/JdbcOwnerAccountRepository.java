package com.salas.musicwave.infrastructure.security;

import com.salas.musicwave.application.download.OwnerAccountRepository;
import com.salas.musicwave.application.download.OwnerIdentity;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcOwnerAccountRepository implements OwnerAccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOwnerAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public OwnerIdentity resolve(String externalSubject, Instant now) {
        if (externalSubject == null || externalSubject.isBlank() || externalSubject.length() > 255) {
            throw new IllegalArgumentException("externalSubject must contain at most 255 characters");
        }
        jdbcTemplate.update(
                """
                INSERT INTO app_user (id, external_subject, created_at)
                VALUES (?, ?, ?)
                ON CONFLICT (external_subject) DO NOTHING
                """,
                UUID.randomUUID(), externalSubject, Timestamp.from(now));
        return jdbcTemplate.queryForObject(
                "SELECT id, external_subject FROM app_user WHERE external_subject = ?",
                (resultSet, rowNumber) -> new OwnerIdentity(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("external_subject")),
                externalSubject);
    }
}
