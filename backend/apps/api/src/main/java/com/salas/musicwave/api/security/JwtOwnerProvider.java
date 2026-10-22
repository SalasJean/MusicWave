package com.salas.musicwave.api.security;

import com.salas.musicwave.application.download.OwnerAccountRepository;
import com.salas.musicwave.application.download.OwnerIdentity;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "musicwave.security.mode", havingValue = "jwt", matchIfMissing = true)
public class JwtOwnerProvider implements CurrentOwnerProvider {

    private final OwnerAccountRepository repository;
    private final Clock clock;

    public JwtOwnerProvider(OwnerAccountRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public OwnerIdentity currentOwner() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token) || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("A validated JWT is required");
        }
        String issuer = token.getToken().getIssuer().toString();
        String subject = token.getToken().getSubject();
        return repository.resolve(issuer + "|" + subject, clock.instant());
    }
}
