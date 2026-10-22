package com.salas.musicwave.api.download;

import com.salas.musicwave.api.error.ApiExceptionHandler;
import com.salas.musicwave.application.execution.DownloadControlResult;
import com.salas.musicwave.application.execution.DownloadControlService;
import com.salas.musicwave.application.execution.InvalidDownloadStateException;
import com.salas.musicwave.domain.download.DownloadStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DownloadControlControllerTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    private DownloadControlService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(DownloadControlService.class);
        DownloadControlController controller = new DownloadControlController(
                service, new LocalOwnerProvider(OWNER_ID, "local-user"));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler(Clock.fixed(
                        Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC)))
                .build();
    }

    @Test
    void activeCancellationReturnsAccepted() throws Exception {
        when(service.cancel(OWNER_ID, JOB_ID)).thenReturn(new DownloadControlResult(
                JOB_ID, DownloadStatus.DOWNLOADING, true));

        mvc.perform(post("/api/v1/downloads/{id}/cancel", JOB_ID))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.status").value("DOWNLOADING"))
                .andExpect(jsonPath("$.cancellationRequested").value(true));
    }

    @Test
    void retryReturnsPending() throws Exception {
        when(service.retry(OWNER_ID, JOB_ID)).thenReturn(new DownloadControlResult(
                JOB_ID, DownloadStatus.PENDING, false));

        mvc.perform(post("/api/v1/downloads/{id}/retry", JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void nonFailedRetryReturnsRfc9457Conflict() throws Exception {
        when(service.retry(OWNER_ID, JOB_ID)).thenThrow(new InvalidDownloadStateException(
                "DOWNLOAD_NOT_RETRYABLE", "Only failed downloads can be retried"));

        mvc.perform(post("/api/v1/downloads/{id}/retry", JOB_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOWNLOAD_NOT_RETRYABLE"));
    }
}
