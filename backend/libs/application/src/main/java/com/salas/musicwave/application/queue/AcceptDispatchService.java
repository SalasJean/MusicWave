package com.salas.musicwave.application.queue;

import java.time.Clock;
import java.util.Objects;

public final class AcceptDispatchService {

    private final QueueRepository repository;
    private final Clock clock;

    public AcceptDispatchService(QueueRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public DeliveryAcceptance accept(DownloadDispatchCommand command, String workerId) {
        Objects.requireNonNull(command, "command");
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId must not be blank");
        }
        return repository.acceptDelivery(command, workerId, clock.instant());
    }
}
