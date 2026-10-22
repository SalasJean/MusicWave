import { describe, it, expect, vi } from 'vitest';
import {
  createSseParserState,
  feedSseChunk,
  type SseRawEvent,
} from './sseParser';

describe('sseParser', () => {
  it('parses standard single event with id, event and data', () => {
    const state = createSseParserState();
    const events: SseRawEvent[] = [];

    feedSseChunk(
      'id: 1720000000000-1\nevent: download\ndata: {"jobId":"uuid-1","status":"DOWNLOADING","progress":25}\n\n',
      state,
      { onEvent: (ev) => events.push(ev) }
    );

    expect(events.length).toBe(1);
    expect(events[0].id).toBe('1720000000000-1');
    expect(events[0].event).toBe('download');
    expect(JSON.parse(events[0].data)).toEqual({
      jobId: 'uuid-1',
      status: 'DOWNLOADING',
      progress: 25,
    });
  });

  it('detects and dispatches comments as heartbeats without emitting events', () => {
    const state = createSseParserState();
    const events: SseRawEvent[] = [];
    const comments: string[] = [];

    feedSseChunk(':heartbeat\n:\n\n', state, {
      onEvent: (ev) => events.push(ev),
      onComment: (c) => comments.push(c),
    });

    expect(events.length).toBe(0);
    expect(comments).toEqual(['heartbeat', '']);
  });

  it('correctly handles chunks split across line and token boundaries (fragmentation)', () => {
    const state = createSseParserState();
    const events: SseRawEvent[] = [];

    // Chunk 1: Partial event name and partial data
    feedSseChunk('id: 100-1\nevent: down', state, {
      onEvent: (ev) => events.push(ev),
    });
    expect(events.length).toBe(0);

    // Chunk 2: Rest of event name, partial JSON data
    feedSseChunk('load\ndata: {"jobId":"chunk-', state, {
      onEvent: (ev) => events.push(ev),
    });
    expect(events.length).toBe(0);

    // Chunk 3: End of JSON and first newline
    feedSseChunk('split"}\n', state, {
      onEvent: (ev) => events.push(ev),
    });
    expect(events.length).toBe(0);

    // Chunk 4: Final newline triggering event dispatch
    feedSseChunk('\n', state, {
      onEvent: (ev) => events.push(ev),
    });

    expect(events.length).toBe(1);
    expect(events[0].id).toBe('100-1');
    expect(events[0].event).toBe('download');
    expect(events[0].data).toBe('{"jobId":"chunk-split"}');
  });

  it('parses multiple events delivered in a single chunk', () => {
    const state = createSseParserState();
    const events: SseRawEvent[] = [];

    const chunk = [
      'id: 1\nevent: download\ndata: {"step":1}\n\n',
      'id: 2\nevent: download\ndata: {"step":2}\n\n',
      'id: 3\nevent: stream-reset\ndata: {"code":"EVENT_HISTORY_EXPIRED"}\n\n',
    ].join('');

    feedSseChunk(chunk, state, {
      onEvent: (ev) => events.push(ev),
    });

    expect(events.length).toBe(3);
    expect(events[0].id).toBe('1');
    expect(events[1].id).toBe('2');
    expect(events[2].id).toBe('3');
    expect(events[2].event).toBe('stream-reset');
    expect(JSON.parse(events[2].data).code).toBe('EVENT_HISTORY_EXPIRED');
  });

  it('joins multi-line data fields with newline', () => {
    const state = createSseParserState();
    const events: SseRawEvent[] = [];

    feedSseChunk('data: line 1\ndata: line 2\ndata: line 3\n\n', state, {
      onEvent: (ev) => events.push(ev),
    });

    expect(events.length).toBe(1);
    expect(events[0].data).toBe('line 1\nline 2\nline 3');
  });

  it('handles CRLF (\\r\\n) and CR (\\r) line endings seamlessly', () => {
    const state = createSseParserState();
    const events: SseRawEvent[] = [];

    feedSseChunk(
      'id: 42\r\nevent: download\r\ndata: {"ok":true}\r\n\r\n',
      state,
      {
        onEvent: (ev) => events.push(ev),
      }
    );

    expect(events.length).toBe(1);
    expect(events[0].id).toBe('42');
    expect(events[0].data).toBe('{"ok":true}');
  });

  it('retains last seen id for subsequent events when omitted', () => {
    const state = createSseParserState();
    const events: SseRawEvent[] = [];

    feedSseChunk('id: initial-id\ndata: first\n\n', state, {
      onEvent: (ev) => events.push(ev),
    });

    feedSseChunk('data: second\n\n', state, {
      onEvent: (ev) => events.push(ev),
    });

    expect(events.length).toBe(2);
    expect(events[0].id).toBe('initial-id');
    expect(events[1].id).toBe('initial-id');
  });

  it('notifies retry parameter when present in stream', () => {
    const state = createSseParserState();
    const onRetry = vi.fn();

    feedSseChunk('retry: 4500\ndata: test\n\n', state, {
      onEvent: () => {},
      onRetry,
    });

    expect(onRetry).toHaveBeenCalledWith(4500);
  });
});
