package com.salas.musicwave.api.queue;

import com.salas.musicwave.api.download.LocalOwnerProvider;
import com.salas.musicwave.application.queue.QueueStartResult;
import com.salas.musicwave.application.queue.StartQueueService;
import com.salas.musicwave.application.execution.DownloadControlService;
import com.salas.musicwave.application.execution.QueueCancellationResult;
import com.salas.musicwave.application.execution.QueueClearResult;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QueueControllerTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Test
    void returnsAcceptedWhenPendingJobsWereQueued() throws Exception {
        StartQueueService service = mock(StartQueueService.class);
        when(service.start(OWNER_ID)).thenReturn(new QueueStartResult(1, List.of(JOB_ID)));
        MockMvc mvc = mvc(service);

        mvc.perform(post("/api/v1/queue/start"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.queuedCount").value(1))
                .andExpect(jsonPath("$.jobIds[0]").value(JOB_ID.toString()));
    }

    @Test
    void repeatedStartReturnsOkAndQueuesNothingAgain() throws Exception {
        StartQueueService service = mock(StartQueueService.class);
        when(service.start(OWNER_ID)).thenReturn(new QueueStartResult(0, List.of()));
        MockMvc mvc = mvc(service);

        mvc.perform(post("/api/v1/queue/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queuedCount").value(0));
    }

    @Test
    void cancelsTheCurrentOwnersQueue() throws Exception {
        StartQueueService startService = mock(StartQueueService.class);
        DownloadControlService controlService = mock(DownloadControlService.class);
        when(controlService.cancelActive(OWNER_ID))
                .thenReturn(new QueueCancellationResult(2, 1));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new QueueController(
                startService,
                new LocalOwnerProvider(OWNER_ID, "local-user"),
                controlService)).build();

        mvc.perform(post("/api/v1/queue/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledCount").value(2))
                .andExpect(jsonPath("$.cancellationRequestedCount").value(1));
    }

    @Test
    void clearsCompletedJobsFromTheVisibleQueue() throws Exception {
        StartQueueService startService = mock(StartQueueService.class);
        DownloadControlService controlService = mock(DownloadControlService.class);
        when(controlService.clearCompleted(OWNER_ID)).thenReturn(new QueueClearResult(4, 0, 0));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new QueueController(
                startService, new LocalOwnerProvider(OWNER_ID, "local-user"), controlService)).build();

        mvc.perform(delete("/api/v1/queue/completed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clearedCount").value(4));
    }

    @Test
    void clearsTheQueueAndReportsCancellationWork() throws Exception {
        StartQueueService startService = mock(StartQueueService.class);
        DownloadControlService controlService = mock(DownloadControlService.class);
        when(controlService.clearAll(OWNER_ID)).thenReturn(new QueueClearResult(5, 2, 1));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new QueueController(
                startService, new LocalOwnerProvider(OWNER_ID, "local-user"), controlService)).build();

        mvc.perform(delete("/api/v1/queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clearedCount").value(5))
                .andExpect(jsonPath("$.cancelledCount").value(2))
                .andExpect(jsonPath("$.cancellationRequestedCount").value(1));
    }

    private MockMvc mvc(StartQueueService service) {
        return MockMvcBuilders.standaloneSetup(new QueueController(
                service,
                new LocalOwnerProvider(OWNER_ID, "local-user"),
                mock(DownloadControlService.class))).build();
    }
}
