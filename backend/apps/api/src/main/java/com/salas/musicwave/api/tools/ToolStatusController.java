package com.salas.musicwave.api.tools;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.salas.musicwave.application.tools.ToolInspection;
import com.salas.musicwave.application.tools.ToolStatusQueryService;
import com.salas.musicwave.application.tools.ToolStatusReport;
import com.salas.musicwave.application.tools.WorkerToolSnapshot;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tools")
public class ToolStatusController {

    private final ToolStatusQueryService queryService;

    public ToolStatusController(ToolStatusQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/status")
    public ToolStatusResponse status() {
        ToolStatusReport report = queryService.query();
        return new ToolStatusResponse(
                report.status().name(),
                report.activeWorkers(),
                report.readyWorkers(),
                report.generatedAt(),
                report.workers().stream().map(this::workerResponse).toList());
    }

    private WorkerToolResponse workerResponse(WorkerToolSnapshot snapshot) {
        return new WorkerToolResponse(
                snapshot.workerId(),
                snapshot.ready(),
                snapshot.startedAt(),
                snapshot.lastSeenAt(),
                snapshot.tools().stream().map(this::toolResponse).toList());
    }

    private ToolResponse toolResponse(ToolInspection inspection) {
        return new ToolResponse(
                inspection.tool().apiName(),
                inspection.available(),
                inspection.executable(),
                inspection.version(),
                inspection.detail());
    }

    public record ToolStatusResponse(
            String status,
            int activeWorkers,
            int readyWorkers,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            Instant generatedAt,
            List<WorkerToolResponse> workers) {
    }

    public record WorkerToolResponse(
            String workerId,
            boolean ready,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            Instant startedAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            Instant lastSeenAt,
            List<ToolResponse> tools) {
    }

    public record ToolResponse(
            String name,
            boolean available,
            String executable,
            String version,
            String detail) {
    }
}
