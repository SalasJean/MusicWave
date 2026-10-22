package com.salas.musicwave.api.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtSecurityConfigurationTest {

    private static final String SECRET = "musicwave-test-secret-with-32-bytes-minimum";
    private static final String ISSUER = "https://identity.example";

    @Test
    void acceptsAProperlySignedTokenWithExpectedIdentityAndAudience() throws Exception {
        JwtDecoder decoder = decoder();

        assertEquals("user-1", decoder.decode(token(
                ISSUER, "user-1", List.of("musicwave"), Instant.now().plusSeconds(60))).getSubject());
    }

    @Test
    void rejectsWrongIssuerAudienceExpiredAndMissingSubject() throws Exception {
        JwtDecoder decoder = decoder();

        assertThrows(JwtException.class, () -> decoder.decode("not-a-jwt"));
        assertThrows(JwtException.class, () -> decoder.decode(token(
                "https://other.example", "user-1", List.of("musicwave"), Instant.now().plusSeconds(60))));
        assertThrows(JwtException.class, () -> decoder.decode(token(
                ISSUER, "user-1", List.of("other"), Instant.now().plusSeconds(60))));
        assertThrows(JwtException.class, () -> decoder.decode(token(
                ISSUER, "user-1", List.of("musicwave"), Instant.now().minusSeconds(60))));
        assertThrows(JwtException.class, () -> decoder.decode(token(
                ISSUER, null, List.of("musicwave"), Instant.now().plusSeconds(60))));
    }

    @Test
    void requiresProductionIdentityConfigurationAndAStrongSharedSecret() {
        JwtSecurityConfiguration configuration = new JwtSecurityConfiguration();

        assertThrows(IllegalStateException.class,
                () -> configuration.jwtDecoder("", "musicwave", "", SECRET));
        assertThrows(IllegalStateException.class,
                () -> configuration.jwtDecoder(ISSUER, "musicwave", "", "too-short"));
        assertThrows(IllegalStateException.class,
                () -> configuration.jwtDecoder(ISSUER, "musicwave", "", ""));
    }

    private JwtDecoder decoder() {
        return new JwtSecurityConfiguration().jwtDecoder(ISSUER, "musicwave", "", SECRET);
    }

    private String token(String issuer,
                         String subject,
                         List<String> audience,
                         Instant expiresAt) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(expiresAt));
        if (subject != null) {
            claims.subject(subject);
        }
        SignedJWT token = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256), claims.build());
        token.sign(new MACSigner(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return token.serialize();
    }
}
