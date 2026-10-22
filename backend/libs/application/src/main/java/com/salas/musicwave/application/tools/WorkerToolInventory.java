package com.salas.musicwave.application.tools;

import java.time.Instant;
import java.util.List;

public interface WorkerToolInventory {

    void save(WorkerToolSnapshot snapshot);

    List<WorkerToolSnapshot> findSeenSince(Instant cutoff);
}
