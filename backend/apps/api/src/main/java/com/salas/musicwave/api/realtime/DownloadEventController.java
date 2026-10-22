package com.salas.musicwave.api.realtime;

import com.salas.musicwave.api.security.CurrentOwnerProvider;
import com.salas.musicwave.application.realtime.StreamCursor;

import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/events")
public class DownloadEventController {

    private final DownloadEventSseService service;
    private final CurrentOwnerProvider ownerProvider;

    public DownloadEventController(DownloadEventSseService service,
                                   CurrentOwnerProvider ownerProvider) {
        this.service = service;
        this.ownerProvider = ownerProvider;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        Optional<StreamCursor> cursor = Optional.ofNullable(lastEventId)
                .filter(value -> !value.isBlank())
                .map(this::parseCursor);
        return service.subscribe(ownerProvider.currentOwner().id(), cursor);
    }

    private StreamCursor parseCursor(String value) {
        try {
            return StreamCursor.parse(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidEventCursorException(
                    "Last-Event-ID must use the Redis Stream <milliseconds>-<sequence> format");
        }
    }
}
