package com.salas.musicwave.api.security;

import com.salas.musicwave.application.download.OwnerIdentity;

public interface CurrentOwnerProvider {

    OwnerIdentity currentOwner();
}
