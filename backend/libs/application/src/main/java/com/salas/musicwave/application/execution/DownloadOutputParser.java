package com.salas.musicwave.application.execution;

import com.salas.musicwave.application.download.DownloadOutputEvent;

import java.util.List;

public interface DownloadOutputParser {

    List<DownloadOutputEvent> parse(String line);
}
