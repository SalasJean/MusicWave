package com.salas.musicwave.application.download;

import java.util.List;

public record CreateDownloadsCommand(OwnerIdentity owner,
                                     DownloadRequestType requestType,
                                     String idempotencyKey,
                                     List<RawUrlInput> inputs) {

    public CreateDownloadsCommand {
        if (owner == null || requestType == null || inputs == null) {
            throw new IllegalArgumentException("owner, requestType and inputs are required");
        }
        inputs = List.copyOf(inputs);
    }
}
