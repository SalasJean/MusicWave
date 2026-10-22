import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { SseClient, type SseDownloadEventData } from './sseClient';

describe('SseClient', () => {
  let client: SseClient;

  beforeEach(() => {
    vi.restoreAllMocks();
    client = new SseClient({
      url: '/api/v1/events',
      initialRetryDelayMs: 50,
      maxRetryDelayMs: 200,
      backoffFactor: 1.5,
      jitterFactor: 0,
    });
  });

  afterEach(() => {
    client.disconnect();
    vi.restoreAllMocks();
  });

  it('connects via fetch, parses download events and updates in-memory cursor', async () => {
    const streamPayload =
      'id: 1720000000000-1\nevent: download\ndata: {"eventId":"1720000000000-1","jobId":"job-uuid-1","status":"DOWNLOADING","progress":55,"speed":"2.1MiB/s","eta":"00:15","title":"Test Video","errorCode":null,"timestamp":"2026-10-14T02:00:00Z"}\n\n';

    const mockStream = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(streamPayload));
        controller.close();
      },
    });

    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      body: mockStream,
    } as never);

    const downloadEvents: SseDownloadEventData[] = [];
    client.onDownload((data) => downloadEvents.push(data));

    client.connect();

    // Allow async stream to process
    await new Promise((resolve) => setTimeout(resolve, 30));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/events',
      expect.objectContaining({
        headers: { Accept: 'text/event-stream' },
      })
    );

    expect(downloadEvents.length).toBe(1);
    expect(downloadEvents[0].jobId).toBe('job-uuid-1');
    expect(downloadEvents[0].progress).toBe(55);
    expect(downloadEvents[0].status).toBe('DOWNLOADING');

    // Memory cursor updated
    expect(client.getLastEventId()).toBe('1720000000000-1');
  });

  it('STRICT INVARIANT: Last-Event-ID is NEVER written to localStorage or sessionStorage', async () => {
    const localSpy = vi.fn();
    const sessionSpy = vi.fn();

    Object.defineProperty(globalThis, 'localStorage', {
      value: { setItem: localSpy, getItem: vi.fn(), removeItem: vi.fn() },
      configurable: true,
      writable: true,
    });
    Object.defineProperty(globalThis, 'sessionStorage', {
      value: { setItem: sessionSpy, getItem: vi.fn(), removeItem: vi.fn() },
      configurable: true,
      writable: true,
    });

    const streamPayload =
      'id: 1720000000000-99\nevent: download\ndata: {"eventId":"1720000000000-99","jobId":"job-99","status":"QUEUED","progress":0}\n\n';

    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      body: new ReadableStream({
        start(controller) {
          controller.enqueue(new TextEncoder().encode(streamPayload));
          controller.close();
        },
      }),
    } as never);

    client.connect();
    await new Promise((resolve) => setTimeout(resolve, 30));

    expect(client.getLastEventId()).toBe('1720000000000-99');
    expect(localSpy).not.toHaveBeenCalled();
    expect(sessionSpy).not.toHaveBeenCalled();
  });

  it('sends Last-Event-ID header on reconnect strictly from memory cursor', async () => {
    let callCount = 0;

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (_url, options) => {
      callCount += 1;
      if (callCount === 1) {
        // First connection delivers an event and then closes
        return {
          ok: true,
          status: 200,
          body: new ReadableStream({
            start(controller) {
              controller.enqueue(
                new TextEncoder().encode(
                  'id: 1720000000000-4\nevent: download\ndata: {"eventId":"1720000000000-4","jobId":"job-1","status":"DOWNLOADING","progress":50}\n\n'
                )
              );
              controller.close();
            },
          }),
        } as never;
      } else {
        // Second connection checks that Last-Event-ID was sent
        const headers = (options as RequestInit).headers as Record<
          string,
          string
        >;
        expect(headers['Last-Event-ID']).toBe('1720000000000-4');

        return {
          ok: true,
          status: 200,
          body: new ReadableStream({
            start(controller) {
              controller.close();
            },
          }),
        } as never;
      }
    });

    client.connect();

    // Wait for initial stream + reconnect timeout
    await new Promise((resolve) => setTimeout(resolve, 150));

    expect(callCount).toBeGreaterThanOrEqual(2);
  });

  it('purges memory cursor upon receiving stream-reset event', async () => {
    const streamPayload = [
      'id: 1720000000000-1\nevent: download\ndata: {"jobId":"j1","status":"QUEUED","progress":0}\n\n',
      'id: 1720000000000-2\nevent: stream-reset\ndata: {"code":"EVENT_HISTORY_EXPIRED","resumeAt":"1720000000000-5"}\n\n',
    ].join('');

    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      body: new ReadableStream({
        start(controller) {
          controller.enqueue(new TextEncoder().encode(streamPayload));
          controller.close();
        },
      }),
    } as never);

    let resetCode: string | null = null;
    client.onStreamReset((data) => {
      resetCode = data.code;
    });

    client.connect();
    await new Promise((resolve) => setTimeout(resolve, 30));

    expect(resetCode).toBe('EVENT_HISTORY_EXPIRED');
    // Cursor MUST be purged to null
    expect(client.getLastEventId()).toBeNull();
  });

  it('handles heartbeats via SSE comments and updates lastHeartbeatAt', async () => {
    const streamPayload = ':heartbeat\n:keepalive\n\n';

    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      body: new ReadableStream({
        start(controller) {
          controller.enqueue(new TextEncoder().encode(streamPayload));
          controller.close();
        },
      }),
    } as never);

    let heartbeatReceived = false;
    client.onHeartbeat(() => {
      heartbeatReceived = true;
    });

    client.connect();
    await new Promise((resolve) => setTimeout(resolve, 30));

    expect(heartbeatReceived).toBe(true);
    expect(client.getLastHeartbeatAt()).toBeInstanceOf(Date);
  });

  it('disconnect() aborts stream and transitions to disconnected without retrying', async () => {
    const abortSpy = vi.fn();

    vi.spyOn(globalThis, 'fetch').mockImplementation((_url, options) => {
      const signal = (options as RequestInit).signal;
      signal?.addEventListener('abort', abortSpy);

      return Promise.resolve({
        ok: true,
        status: 200,
        body: new ReadableStream({
          start() {
            // Keep stream open
          },
        }),
      } as never);
    });

    client.connect();
    await new Promise((resolve) => setTimeout(resolve, 10));

    expect(client.getStatus()).toBe('connected');

    client.disconnect();

    expect(abortSpy).toHaveBeenCalled();
    expect(client.getStatus()).toBe('disconnected');
  });

  it('purges cursor on HTTP 400 INVALID_EVENT_CURSOR', async () => {
    // Simulate invalid cursor rejection
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 400,
      statusText: 'Bad Request',
      json: async () => ({ code: 'INVALID_EVENT_CURSOR' }),
    } as never);

    // Set arbitrary cursor before connect
    (client as unknown as { lastEventId: string }).lastEventId = 'invalid-id';

    client.connect();
    await new Promise((resolve) => setTimeout(resolve, 20));

    expect(client.getLastEventId()).toBeNull();
  });
});
