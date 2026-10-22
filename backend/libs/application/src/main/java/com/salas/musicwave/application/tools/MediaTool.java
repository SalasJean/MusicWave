package com.salas.musicwave.application.tools;

public enum MediaTool {
    YT_DLP("ytDlp"),
    FFMPEG("ffmpeg");

    private final String apiName;

    MediaTool(String apiName) {
        this.apiName = apiName;
    }

    public String apiName() {
        return apiName;
    }
}
