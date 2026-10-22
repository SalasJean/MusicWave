package com.salas.musicwave.api.security;

import com.salas.musicwave.application.download.OwnerAccountRepository;
import com.salas.musicwave.application.download.OwnerIdentity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtOwnerProviderTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesTheOwnerFromTheValidatedIssuerAndSubject() {
        UUID ownerId = UUID.randomUUID();
        CapturingRepository repository = new CapturingRepository(ownerId);
        Jwt jwt = new Jwt(
                "token", NOW.minusSeconds(1), NOW.plusSeconds(60),
                Map.of("alg", "HS256"),
                Map.of("iss", "https://identity.example", "sub", "user-42", "aud", List.of("musicwave")));
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, List.of(), "user-42");
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        OwnerIdentity owner = new JwtOwnerProvider(
                repository, Clock.fixed(NOW, ZoneOffset.UTC)).currentOwner();

        assertEquals(ownerId, owner.id());
        assertEquals("https://identity.example|user-42", repository.externalSubject);
        assertEquals(NOW, repository.seenAt);
    }

    @Test
    void failsClosedWithoutAValidatedJwtAuthentication() {
        JwtOwnerProvider provider = new JwtOwnerProvider(
                new CapturingRepository(UUID.randomUUID()), Clock.systemUTC());

        assertThrows(AuthenticationCredentialsNotFoundException.class, provider::currentOwner);
    }

    private static final class CapturingRepository implements OwnerAccountRepository {
        private final UUID ownerId;
        private String externalSubject;
        private Instant seenAt;

        private CapturingRepository(UUID ownerId) {
            this.ownerId = ownerId;
        }

        @Override
        public OwnerIdentity resolve(String externalSubject, Instant seenAt) {
            this.externalSubject = externalSubject;
            this.seenAt = seenAt;
            return new OwnerIdentity(ownerId, externalSubject);
        }
    }
}
