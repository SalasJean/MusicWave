package com.salas.musicwave.application.download;

import java.time.Instant;

public interface OwnerAccountRepository {

    OwnerIdentity resolve(String externalSubject, Instant now);
}
