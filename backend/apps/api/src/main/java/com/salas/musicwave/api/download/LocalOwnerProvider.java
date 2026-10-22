package com.salas.musicwave.api.download;

import com.salas.musicwave.api.security.CurrentOwnerProvider;
import com.salas.musicwave.application.download.OwnerIdentity;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "musicwave.security.mode", havingValue = "local")
public class LocalOwnerProvider implements CurrentOwnerProvider {

    private final OwnerIdentity owner;

    public LocalOwnerProvider(
            @Value("${musicwave.security.local-owner-id}") UUID ownerId,
            @Value("${musicwave.security.local-owner-subject}") String externalSubject) {
        this.owner = new OwnerIdentity(ownerId, externalSubject);
    }

    @Override
    public OwnerIdentity currentOwner() {
        return owner;
    }
}
