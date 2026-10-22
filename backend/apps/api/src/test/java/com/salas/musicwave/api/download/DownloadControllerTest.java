package com.salas.musicwave.api.download;

import com.salas.musicwave.api.error.ApiExceptionHandler;
import com.salas.musicwave.application.download.CreateDownloadsCommand;
import com.salas.musicwave.application.download.CreateDownloadsResult;
import com.salas.musicwave.application.download.CreateDownloadsService;
import com.salas.musicwave.application.download.DownloadQueryService;
import com.salas.musicwave.application.download.IdempotencyConflictException;
import com.salas.musicwave.application.download.RejectedUrl;
import com.salas.musicwave.domain.download.DownloadJob;
import com.salas.musicwave.domain.download.SourceUrl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DownloadControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    private CreateDownloadsService createService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        createService = mock(CreateDownloadsService.class);
        DownloadQueryService queryService = mock(DownloadQueryService.class);
        DownloadController controller = new DownloadController(
                createService,
                queryService,
                new LocalOwnerProvider(OWNER_ID, "local-user"));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler(clock))
                .build();
    }

    @Test
    void createsOnePendingJobAndPublishesItsLocation() throws Exception {
        when(createService.create(any(CreateDownloadsCommand.class)))
                .thenReturn(new CreateDownloadsResult(
                        REQUEST_ID, false, List.of(job("https://example.com/audio")), List.of()));

        mvc.perform(post("/api/v1/downloads")
                        .header("Idempotency-Key", "one-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/audio\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/downloads/" + JOB_ID))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID.toString()))
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.jobs[0].status").value("PENDING"));

        ArgumentCaptor<CreateDownloadsCommand> command = ArgumentCaptor.forClass(CreateDownloadsCommand.class);
        org.mockito.Mockito.verify(createService).create(command.capture());
        assertEquals(OWNER_ID, command.getValue().owner().id());
        assertEquals("one-1", command.getValue().idempotencyKey());
    }

    @Test
    void returnsPartialBulkDetails() throws Exception {
        when(createService.create(any(CreateDownloadsCommand.class)))
                .thenReturn(new CreateDownloadsResult(
                        REQUEST_ID,
                        false,
                        List.of(job("https://example.com/audio")),
                        List.of(new RejectedUrl(2, "ftp://invalid", "URL_SCHEME_NOT_ALLOWED",
                                "Only HTTP and HTTPS URLs are accepted"))));

        mvc.perform(post("/api/v1/downloads/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urls\":[\"https://example.com/audio\",\"ftp://invalid\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.rejectedCount").value(1))
                .andExpect(jsonPath("$.rejected[0].position").value(2))
                .andExpect(jsonPath("$.rejected[0].code").value("URL_SCHEME_NOT_ALLOWED"));
    }

    @Test
    void decodesUtf8BomAndPreservesTxtLineNumbers() throws Exception {
        when(createService.create(any(CreateDownloadsCommand.class)))
                .thenReturn(new CreateDownloadsResult(
                        REQUEST_ID, false, List.of(job("https://youtu.be/abc")), List.of()));
        byte[] content = ("\uFEFFhttps://youtu.be/abc\r\n\r\nftp://invalid\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "urls.txt", "text/plain", content);

        mvc.perform(multipart("/api/v1/downloads/import").file(file))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateDownloadsCommand> command = ArgumentCaptor.forClass(CreateDownloadsCommand.class);
        org.mockito.Mockito.verify(createService).create(command.capture());
        assertEquals(2, command.getValue().inputs().size());
        assertEquals(1, command.getValue().inputs().get(0).position());
        assertEquals("https://youtu.be/abc", command.getValue().inputs().get(0).value());
        assertEquals(3, command.getValue().inputs().get(1).position());
    }

    @Test
    void mapsIdempotencyConflictsToRfc9457() throws Exception {
        when(createService.create(any(CreateDownloadsCommand.class)))
                .thenThrow(new IdempotencyConflictException());

        mvc.perform(post("/api/v1/downloads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/audio\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"))
                .andExpect(jsonPath("$.instance").value("/api/v1/downloads"));
    }

    @Test
    void rejectsMalformedUtf8Imports() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "urls.txt", "text/plain", new byte[]{(byte) 0xC3, 0x28});

        mvc.perform(multipart("/api/v1/downloads/import").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_NOT_UTF8"));
    }

    @Test
    void rejectsOversizedImports() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "urls.txt",
                "text/plain",
                new byte[DownloadController.MAX_IMPORT_BYTES + 1]);

        mvc.perform(multipart("/api/v1/downloads/import").file(file))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("IMPORT_TOO_LARGE"));
    }

    private DownloadJob job(String url) {
        return DownloadJob.pending(
                JOB_ID, OWNER_ID, REQUEST_ID, 0, SourceUrl.parse(url), NOW);
    }
}
