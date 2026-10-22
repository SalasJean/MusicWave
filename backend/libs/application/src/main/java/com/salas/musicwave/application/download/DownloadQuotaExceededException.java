package com.salas.musicwave.application.download;

public final class DownloadQuotaExceededException extends RuntimeException {

    private final int limit;

    public DownloadQuotaExceededException(int limit) {
        super("The active download quota of " + limit + " jobs would be exceeded");
        this.limit = limit;
    }

    public int limit() {
        return limit;
    }
}
