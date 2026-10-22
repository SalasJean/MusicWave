package com.salas.musicwave.api.download;

import com.salas.musicwave.api.error.ApiExceptionHandler;
import com.salas.musicwave.application.artifact.ArtifactDownload;
import com.salas.musicwave.application.artifact.ArtifactDeliveryInProgressException;
import com.salas.musicwave.application.artifact.ArtifactMissingException;
import com.salas.musicwave.application.artifact.ArtifactNotReadyException;
import com.salas.musicwave.application.artifact.ArtifactStorageUnavailableException;
import com.salas.musicwave.application.artifact.ArtifactUnavailableException;
import com.salas.musicwave.application.artifact.GetArtifactService;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ArtifactControllerTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Test
    void streamsMp3WithSafeDownloadHeaders() throws Exception {
        GetArtifactService service = mock(GetArtifactService.class);
        when(service.get(OWNER_ID, JOB_ID)).thenReturn(new ArtifactDownload(
                "My Song.mp3", "audio/mpeg", 3, "a".repeat(64),
                new ByteArrayInputStream(new byte[]{1, 2, 3})));

        mvc(service).perform(get("/api/v1/downloads/{id}/artifact", JOB_ID))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{1, 2, 3}))
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(header().string("Content-Length", "3"))
                .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''My%20Song.mp3")))
                .andExpect(header().string("Cache-Control", "no-store, no-cache, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Expires", "0"))
                .andExpect(header().string("Accept-Ranges", "none"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Content-SHA256", "a".repeat(64)));
        verify(service).completeDelivery(any());
    }

    @Test
    void mapsNotReadyToStableProblemDetail() throws Exception {
        GetArtifactService service = mock(GetArtifactService.class);
        when(service.get(any(), any())).thenThrow(new ArtifactNotReadyException());

        mvc(service).perform(get("/api/v1/downloads/{id}/artifact", JOB_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARTIFACT_NOT_READY"));
    }

    @Test
    void mapsMissingObjectToStableProblemDetail() throws Exception {
        GetArtifactService service = mock(GetArtifactService.class);
        when(service.get(any(), any())).thenThrow(new ArtifactMissingException("missing"));

        mvc(service).perform(get("/api/v1/downloads/{id}/artifact", JOB_ID))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("ARTIFACT_MISSING"));
    }

    @Test
    void mapsStorageOutageWithoutLeakingItsCause() throws Exception {
        GetArtifactService service = mock(GetArtifactService.class);
        when(service.get(any(), any())).thenThrow(new ArtifactStorageUnavailableException(
                "secret provider detail", new IllegalStateException("offline")));

        mvc(service).perform(get("/api/v1/downloads/{id}/artifact", JOB_ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ARTIFACT_STORAGE_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("Artifact storage is temporarily unavailable"));
    }

    @Test
    void mapsConcurrentDeliveryToStableConflict() throws Exception {
        GetArtifactService service = mock(GetArtifactService.class);
        when(service.get(any(), any())).thenThrow(new ArtifactDeliveryInProgressException());

        mvc(service).perform(get("/api/v1/downloads/{id}/artifact", JOB_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARTIFACT_DELIVERY_IN_PROGRESS"));
    }

    @Test
    void mapsConsumedArtifactToGone() throws Exception {
        GetArtifactService service = mock(GetArtifactService.class);
        when(service.get(any(), any())).thenThrow(new ArtifactUnavailableException(
                "ARTIFACT_ALREADY_DOWNLOADED", "already delivered"));

        mvc(service).perform(get("/api/v1/downloads/{id}/artifact", JOB_ID))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("ARTIFACT_ALREADY_DOWNLOADED"));
    }

    private MockMvc mvc(GetArtifactService service) {
        ArtifactController controller = new ArtifactController(
                service, new LocalOwnerProvider(OWNER_ID, "local-user"));
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler(
                        Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC)))
                .build();
    }
}
