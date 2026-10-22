import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  createDownload,
  createBulkDownloads,
  importDownloadsText,
  getDownloads,
  getDownloadById,
  cancelDownload,
  retryDownload,
  startQueue,
  cancelQueue,
  clearCompletedQueue,
  clearQueue,
  getSystemStatus,
  getToolStatus,
} from './downloadsApi';

describe('downloadsApi adapter', () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it('createDownload calls POST /api/v1/downloads with body and idempotency key', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ createdCount: 1, jobs: [] }), {
        status: 201,
        headers: new Headers({ 'Content-Type': 'application/json' }),
      })
    );

    await createDownload(
      { url: 'https://www.youtube.com/watch?v=BaW_jenozKc' },
      { idempotencyKey: 'idemp-1' }
    );

    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/downloads',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          url: 'https://www.youtube.com/watch?v=BaW_jenozKc',
        }),
        headers: expect.any(Headers),
      })
    );
  });

  it('createBulkDownloads calls POST /api/v1/downloads/bulk', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ createdCount: 2 }), {
        status: 201,
        headers: new Headers({ 'Content-Type': 'application/json' }),
      })
    );

    await createBulkDownloads({
      urls: ['https://example.com/1', 'https://example.com/2'],
    });

    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/downloads/bulk',
      expect.objectContaining({
        method: 'POST',
      })
    );
  });

  it('importDownloadsText sends FormData to POST /api/v1/downloads/import', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ createdCount: 1 }), {
        status: 201,
        headers: new Headers({ 'Content-Type': 'application/json' }),
      })
    );

    const blob = new Blob(['https://example.com/3\n'], { type: 'text/plain' });
    await importDownloadsText(blob);

    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/downloads/import',
      expect.objectContaining({
        method: 'POST',
        body: expect.any(FormData),
      })
    );
  });

  it('getDownloads passes page and size query parameters', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          items: [],
          page: 1,
          size: 10,
          totalElements: 0,
          totalPages: 0,
        }),
        {
          status: 200,
          headers: new Headers({ 'Content-Type': 'application/json' }),
        }
      )
    );

    await getDownloads({ page: 1, size: 10 });

    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/downloads?page=1&size=10',
      expect.objectContaining({ method: 'GET' })
    );
  });

  it('getDownloadById requests specific job UUID', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ id: 'uuid-123' }), {
        status: 200,
        headers: new Headers({ 'Content-Type': 'application/json' }),
      })
    );

    await getDownloadById('uuid-123');

    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/downloads/uuid-123',
      expect.objectContaining({ method: 'GET' })
    );
  });

  it('cancelDownload and retryDownload invoke respective endpoints', async () => {
    globalThis.fetch = vi.fn().mockImplementation(() =>
      Promise.resolve(
        new Response(JSON.stringify({ id: 'uuid-123', status: 'CANCELLED' }), {
          status: 200,
          headers: new Headers({ 'Content-Type': 'application/json' }),
        })
      )
    );

    await cancelDownload('uuid-123');
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/downloads/uuid-123/cancel',
      expect.objectContaining({ method: 'POST' })
    );

    await retryDownload('uuid-123');
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/downloads/uuid-123/retry',
      expect.objectContaining({ method: 'POST' })
    );
  });

  it('startQueue, cancelQueue, clearCompletedQueue, clearQueue invoke queue endpoints', async () => {
    globalThis.fetch = vi.fn().mockImplementation(() =>
      Promise.resolve(
        new Response(JSON.stringify({ queuedCount: 2 }), {
          status: 200,
          headers: new Headers({ 'Content-Type': 'application/json' }),
        })
      )
    );

    await startQueue();
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/queue/start',
      expect.objectContaining({ method: 'POST' })
    );

    await cancelQueue();
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/queue/cancel',
      expect.objectContaining({ method: 'POST' })
    );

    await clearCompletedQueue();
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/queue/completed',
      expect.objectContaining({ method: 'DELETE' })
    );

    await clearQueue();
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/queue',
      expect.objectContaining({ method: 'DELETE' })
    );
  });

  it('getSystemStatus and getToolStatus query operational endpoints', async () => {
    globalThis.fetch = vi.fn().mockImplementation(() =>
      Promise.resolve(
        new Response(JSON.stringify({ status: 'UP' }), {
          status: 200,
          headers: new Headers({ 'Content-Type': 'application/json' }),
        })
      )
    );

    await getSystemStatus();
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/system/status',
      expect.objectContaining({ method: 'GET' })
    );

    await getToolStatus();
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/tools/status',
      expect.objectContaining({ method: 'GET' })
    );
  });
});
