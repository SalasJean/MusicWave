import { env } from '@/shared/config/env';
import {
  createSseParserState,
  feedSseChunk,
  type SseRawEvent,
} from './sseParser';

export type SseConnectionStatus =
  'disconnected' | 'connecting' | 'connected' | 'reconnecting';

export interface SseDownloadEventData {
  eventId: string;
  jobId: string;
  status: string;
  progress: number;
  speed: string | null;
  eta: string | null;
  title: string | null;
  errorCode: string | null;
  timestamp: string;
}

export interface SseStreamResetData {
  code: string;
  resumeAt?: string;
}

export interface SseClientConfig {
  url?: string;
  initialRetryDelayMs?: number;
  maxRetryDelayMs?: number;
  backoffFactor?: number;
  jitterFactor?: number;
}

export class SseClient extends EventTarget {
  private status: SseConnectionStatus = 'disconnected';

  /**
   * Stored STRICTLY in memory as required by ADR and prompt specifications.
   * NEVER persisted to localStorage or sessionStorage.
   */
  private lastEventId: string | null = null;

  private abortController: AbortController | null = null;
  private retryTimeoutId: ReturnType<typeof setTimeout> | null = null;
  private retryAttempt = 0;
  private serverRetryMs: number | null = null;
  private lastHeartbeatAt: Date | null = null;

  private readonly url: string;
  private readonly initialRetryDelayMs: number;
  private readonly maxRetryDelayMs: number;
  private readonly backoffFactor: number;
  private readonly jitterFactor: number;

  constructor(config: SseClientConfig = {}) {
    super();
    this.url = config.url ?? `${env.apiBasePath}/events`;
    this.initialRetryDelayMs = config.initialRetryDelayMs ?? 1000;
    this.maxRetryDelayMs = config.maxRetryDelayMs ?? 30000;
    this.backoffFactor = config.backoffFactor ?? 1.5;
    this.jitterFactor = config.jitterFactor ?? 0.2;
  }

  getStatus(): SseConnectionStatus {
    return this.status;
  }

  getLastEventId(): string | null {
    return this.lastEventId;
  }

  getLastHeartbeatAt(): Date | null {
    return this.lastHeartbeatAt;
  }

  private setStatus(newStatus: SseConnectionStatus): void {
    if (this.status !== newStatus) {
      this.status = newStatus;
      this.dispatchEvent(
        new CustomEvent('statusChange', { detail: { status: newStatus } })
      );
    }
  }

  /**
   * Connects to the SSE endpoint.
   * If already connecting or connected, does nothing.
   */
  connect(): void {
    if (this.status === 'connecting' || this.status === 'connected') {
      return;
    }

    this.clearRetryTimer();
    this.startStreaming();
  }

  /**
   * Disconnects and cleans up resources.
   * Cancels in-flight requests and scheduled reconnection attempts.
   */
  disconnect(): void {
    this.clearRetryTimer();
    if (this.abortController) {
      this.abortController.abort();
      this.abortController = null;
    }
    this.setStatus('disconnected');
  }

  /**
   * Manually resets the last event cursor in memory.
   */
  resetCursor(): void {
    this.lastEventId = null;
  }

  private async startStreaming(): Promise<void> {
    this.abortController = new AbortController();
    const signal = this.abortController.signal;

    this.setStatus(this.retryAttempt > 0 ? 'reconnecting' : 'connecting');

    const headers: Record<string, string> = {
      Accept: 'text/event-stream',
    };

    if (this.lastEventId) {
      headers['Last-Event-ID'] = this.lastEventId;
    }

    try {
      const response = await fetch(this.url, {
        method: 'GET',
        headers,
        signal,
      });

      // Handle HTTP error responses
      if (!response.ok) {
        if (response.status === 400) {
          // If 400 INVALID_EVENT_CURSOR, purge cursor and retry from tail
          this.lastEventId = null;
        }

        throw new Error(
          `SSE connection failed with HTTP ${response.status}: ${response.statusText}`
        );
      }

      if (!response.body) {
        throw new Error('SSE response body is null');
      }

      // Connection established successfully
      this.setStatus('connected');
      this.retryAttempt = 0;

      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8');
      const parserState = createSseParserState();

      while (true) {
        const { value, done } = await reader.read();
        if (done) {
          break;
        }

        const chunk = decoder.decode(value, { stream: true });
        feedSseChunk(chunk, parserState, {
          onEvent: (rawEvent) => this.handleRawEvent(rawEvent),
          onComment: () => this.handleHeartbeat(),
          onRetry: (retryMs) => {
            this.serverRetryMs = retryMs;
          },
        });
      }

      // Stream closed cleanly by server; attempt reconnect
      this.scheduleReconnect();
    } catch (err: unknown) {
      if (signal.aborted) {
        // Deliberately disconnected
        return;
      }

      this.dispatchEvent(new CustomEvent('error', { detail: { error: err } }));
      this.scheduleReconnect();
    }
  }

  private handleRawEvent(raw: SseRawEvent): void {
    // Update cursor strictly in memory if id is present
    if (raw.id) {
      this.lastEventId = raw.id;
    }

    if (raw.event === 'stream-reset') {
      try {
        const resetData: SseStreamResetData = JSON.parse(raw.data);
        // Purge memory cursor upon stream-reset per contract
        this.lastEventId = null;
        this.dispatchEvent(
          new CustomEvent('streamReset', { detail: resetData })
        );
      } catch {
        this.lastEventId = null;
        this.dispatchEvent(
          new CustomEvent('streamReset', {
            detail: { code: 'EVENT_HISTORY_EXPIRED' },
          })
        );
      }
      return;
    }

    if (raw.event === 'download') {
      try {
        const downloadData: SseDownloadEventData = JSON.parse(raw.data);
        // Also capture eventId if present in data body
        if (downloadData.eventId) {
          this.lastEventId = downloadData.eventId;
        }
        this.dispatchEvent(
          new CustomEvent('download', { detail: downloadData })
        );
      } catch {
        // Skip unparseable payload
      }
      return;
    }

    // Generic custom event dispatch
    this.dispatchEvent(new CustomEvent(raw.event, { detail: raw.data }));
  }

  private handleHeartbeat(): void {
    this.lastHeartbeatAt = new Date();
    this.dispatchEvent(
      new CustomEvent('heartbeat', {
        detail: { timestamp: this.lastHeartbeatAt },
      })
    );
  }

  private scheduleReconnect(): void {
    if (this.status === 'disconnected') {
      return;
    }

    this.setStatus('reconnecting');
    this.retryAttempt += 1;

    let delay = this.serverRetryMs;
    if (delay === null || delay === undefined) {
      // Exponential backoff
      const baseDelay =
        this.initialRetryDelayMs *
        Math.pow(this.backoffFactor, this.retryAttempt - 1);
      const boundedDelay = Math.min(baseDelay, this.maxRetryDelayMs);

      // Jitter: +/- jitterFactor
      const jitter = boundedDelay * this.jitterFactor * (Math.random() * 2 - 1);
      delay = Math.max(100, Math.floor(boundedDelay + jitter));
    }

    this.clearRetryTimer();
    this.retryTimeoutId = setTimeout(() => {
      this.startStreaming();
    }, delay);
  }

  private clearRetryTimer(): void {
    if (this.retryTimeoutId !== null) {
      clearTimeout(this.retryTimeoutId);
      this.retryTimeoutId = null;
    }
  }

  // Type-safe convenience subscription helpers
  onStatusChange(callback: (status: SseConnectionStatus) => void): () => void {
    const handler = (e: Event) => {
      const custom = e as CustomEvent<{ status: SseConnectionStatus }>;
      callback(custom.detail.status);
    };
    this.addEventListener('statusChange', handler);
    return () => this.removeEventListener('statusChange', handler);
  }

  onDownload(callback: (data: SseDownloadEventData) => void): () => void {
    const handler = (e: Event) => {
      const custom = e as CustomEvent<SseDownloadEventData>;
      callback(custom.detail);
    };
    this.addEventListener('download', handler);
    return () => this.removeEventListener('download', handler);
  }

  onStreamReset(callback: (data: SseStreamResetData) => void): () => void {
    const handler = (e: Event) => {
      const custom = e as CustomEvent<SseStreamResetData>;
      callback(custom.detail);
    };
    this.addEventListener('streamReset', handler);
    return () => this.removeEventListener('streamReset', handler);
  }

  onHeartbeat(callback: (data: { timestamp: Date }) => void): () => void {
    const handler = (e: Event) => {
      const custom = e as CustomEvent<{ timestamp: Date }>;
      callback(custom.detail);
    };
    this.addEventListener('heartbeat', handler);
    return () => this.removeEventListener('heartbeat', handler);
  }
}

// Global SSE client instance for frontend lifecycle
export const sseClient = new SseClient();
