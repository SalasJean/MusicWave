package com.salas.musicwave.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "musicwave.security.mode", havingValue = "jwt", matchIfMissing = true)
public class JwtSecurityConfiguration {

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${musicwave.security.jwt.issuer:}") String issuer,
            @Value("${musicwave.security.jwt.audience:}") String audience,
            @Value("${musicwave.security.jwt.jwk-set-uri:}") String jwkSetUri,
            @Value("${musicwave.security.jwt.hmac-secret:}") String hmacSecret) {
        if (issuer.isBlank() || audience.isBlank()) {
            throw new IllegalStateException("JWT issuer and audience must be configured");
        }

        NimbusJwtDecoder decoder;
        if (!hmacSecret.isBlank()) {
            if (hmacSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException("JWT HMAC secret must contain at least 32 bytes");
            }
            SecretKey key = new SecretKeySpec(
                    hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            decoder = NimbusJwtDecoder.withSecretKey(key)
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build();
        } else if (!jwkSetUri.isBlank()) {
            decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        } else {
            throw new IllegalStateException("JWT JWK set URI or HMAC secret must be configured");
        }

        OAuth2TokenValidator<Jwt> issuerAndTime = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                "aud", audiences -> audiences != null && audiences.contains(audience));
        OAuth2TokenValidator<Jwt> identityValidator = jwt -> {
            String subject = jwt.getSubject();
            String tokenIssuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
            boolean valid = subject != null && !subject.isBlank()
                    && tokenIssuer != null
                    && (tokenIssuer.length() + subject.length() + 1) <= 255;
            return valid
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                            "invalid_token", "JWT identity claims are invalid", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerAndTime, audienceValidator, identityValidator));
        return decoder;
    }

    @Bean
    SecurityFilterChain jwtSecurityFilterChain(
            HttpSecurity http,
            JwtDecoder decoder,
            ObjectMapper objectMapper,
            Clock clock) throws Exception {
        SecurityProblemWriter problems = new SecurityProblemWriter(objectMapper, clock);
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/prometheus",
                                "/v3/api-docs/**",
                                "/api/v1/system/status",
                                "/api/v1/tools/status",
                                "/error")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(decoder))
                        .authenticationEntryPoint((request, response, exception) -> problems.write(
                                request, response, 401, "Unauthorized", "AUTHENTICATION_REQUIRED",
                                "A valid bearer token is required"))
                        .accessDeniedHandler((request, response, exception) -> problems.write(
                                request, response, 403, "Forbidden", "ACCESS_DENIED",
                                "The authenticated identity cannot access this resource")))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> problems.write(
                                request, response, 401, "Unauthorized", "AUTHENTICATION_REQUIRED",
                                "A valid bearer token is required"))
                        .accessDeniedHandler((request, response, exception) -> problems.write(
                                request, response, 403, "Forbidden", "ACCESS_DENIED",
                                "The authenticated identity cannot access this resource")))
                .build();
    }
}
