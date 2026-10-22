package com.salas.musicwave.application.download;

import com.salas.musicwave.domain.download.DownloadJob;

import java.util.List;

public record DownloadJobPage(List<DownloadJob> items,
                              int page,
                              int size,
                              long totalElements) {

    public DownloadJobPage {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
