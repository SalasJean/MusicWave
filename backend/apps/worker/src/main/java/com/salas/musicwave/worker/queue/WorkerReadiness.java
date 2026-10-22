package com.salas.musicwave.worker.queue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

@Component
public class WorkerReadiness {

    private final AtomicBoolean ready = new AtomicBoolean();

    public boolean isReady() {
        return ready.get();
    }

    public void update(boolean value) {
        ready.set(value);
    }
}
