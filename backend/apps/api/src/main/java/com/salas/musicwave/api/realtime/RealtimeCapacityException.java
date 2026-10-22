package com.salas.musicwave.api.realtime;

public final class RealtimeCapacityException extends RuntimeException {

    public RealtimeCapacityException() {
        super("Realtime connection capacity is temporarily exhausted");
    }
}
