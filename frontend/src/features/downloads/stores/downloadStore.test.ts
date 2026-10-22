import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { DownloadStore } from './downloadStore';
import * as downloadsApi from '@/features/downloads/api/downloadsApi';
import * as artifactManager from '@/features/downloads/lib/artifactManager';
import { ApiProblemError } from '@/shared/api/problemDetails';

describe('DownloadStore', () => {
  let store: DownloadStore;

  beforeEach(() => {
    vi.restoreAllMocks();
    store = new DownloadStore();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('loadPage fetches downloads and updates store state with event dispatch', async () => {
    const mockResponse = {
      data: {
        items: [
          {
            id: 'job-1',
            sourceUrl: 'https://youtube.com/watch?v=abc',
            status: 'PENDING',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
      meta: { status: 200, statusText: 'OK' },
    };

    vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue(
      mockResponse as never
    );

    const listener = vi.fn();
    store.addEventListener('stateChange', listener);

    await store.loadPage(0, 20);

    const state = store.getState();
    expect(state.items.length).toBe(1);
    expect(state.items[0].id).toBe('job-1');
    expect(state.totalElements).toBe(1);
    expect(state.loading).toBe(false);
    expect(state.error).toBeNull();
    expect(listener).toHaveBeenCalled();
  });

  it('loadPage handles ApiProblemError gracefully without crashing', async () => {
    const problemError = new ApiProblemError({
      type: 'about:blank',
      title: 'Not Found',
      status: 404,
      detail: 'No se encontraron descargas.',
      code: 'DOWNLOAD_NOT_FOUND',
    });

    vi.spyOn(downloadsApi, 'getDownloads').mockRejectedValue(problemError);

    await store.loadPage(0, 20);

    const state = store.getState();
    expect(state.loading).toBe(false);
    expect(state.error).toBe('No se encontraron descargas.');
  });

  it('addSingle creates a job with idempotency key and reloads page 0', async () => {
    const createMock = vi
      .spyOn(downloadsApi, 'createDownload')
      .mockResolvedValue({
        data: {
          requestId: 'req-1',
          replayed: false,
          createdCount: 1,
          rejectedCount: 0,
          jobs: [
            {
              id: 'job-new',
              sourceUrl: 'https://youtube.com/watch?v=1',
              status: 'PENDING',
            },
          ],
          rejected: [],
        },
        meta: { status: 201, statusText: 'Created' },
      } as never);

    const getMock = vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: {
        items: [
          {
            id: 'job-new',
            sourceUrl: 'https://youtube.com/watch?v=1',
            status: 'PENDING',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    const summary = await store.addSingle('https://youtube.com/watch?v=1');

    expect(createMock).toHaveBeenCalledWith(
      { url: 'https://youtube.com/watch?v=1' },
      expect.objectContaining({
        idempotencyKey: expect.stringMatching(/^single-/),
      })
    );

    expect(getMock).toHaveBeenCalledWith({ page: 0, size: 20 });
    expect(summary.createdCount).toBe(1);
    expect(summary.createdJobs.length).toBe(1);
  });

  it('addBulk submits list of URLs with partial acceptance', async () => {
    vi.spyOn(downloadsApi, 'createBulkDownloads').mockResolvedValue({
      data: {
        requestId: 'req-bulk-1',
        replayed: false,
        createdCount: 1,
        rejectedCount: 1,
        jobs: [
          { id: 'job-1', sourceUrl: 'https://valid.com', status: 'PENDING' },
        ],
        rejected: [
          {
            position: 2,
            value: 'invalid',
            code: 'URL_INVALID',
            message: 'Invalid',
          },
        ],
      },
      meta: { status: 201, statusText: 'Created' },
    } as never);

    vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: { items: [], page: 0, size: 20, totalElements: 1, totalPages: 1 },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    const summary = await store.addBulk(['https://valid.com', 'invalid']);

    expect(summary.createdCount).toBe(1);
    expect(summary.rejectedCount).toBe(1);
    expect(summary.rejectedItems[0].position).toBe(2);
  });

  it('importTxt submits file to import API', async () => {
    const mockFile = new Blob(['url1\nurl2\n'], { type: 'text/plain' });

    vi.spyOn(downloadsApi, 'importDownloadsText').mockResolvedValue({
      data: {
        requestId: 'req-txt-1',
        replayed: false,
        createdCount: 2,
        rejectedCount: 0,
        jobs: [],
        rejected: [],
      },
      meta: { status: 201, statusText: 'Created' },
    } as never);

    vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: { items: [], page: 0, size: 20, totalElements: 2, totalPages: 1 },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    const summary = await store.importTxt(mockFile);

    expect(summary.createdCount).toBe(2);
    expect(summary.rejectedCount).toBe(0);
  });

  /* Queue controls, actions and protection tests. */

  it('startQueue handles 202 asynchronous acceptance and refreshes view', async () => {
    const startMock = vi.spyOn(downloadsApi, 'startQueue').mockResolvedValue({
      data: { queuedCount: 3, jobIds: ['j-1', 'j-2', 'j-3'] },
      meta: { status: 202, statusText: 'Accepted' },
    } as never);

    const getMock = vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: { items: [], page: 0, size: 20, totalElements: 3, totalPages: 1 },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    const result = await store.startQueue();

    expect(startMock).toHaveBeenCalledTimes(1);
    expect(getMock).toHaveBeenCalled();
    expect(result.queuedCount).toBe(3);
    const state = store.getState();
    expect(state.feedback?.type).toBe('success');
    expect(state.feedback?.message).toContain('3 descarga(s) encolada(s)');
    expect(state.isOperating).toBe(false);
  });

  it('startQueue handles 200 no-op when nothing is pending', async () => {
    vi.spyOn(downloadsApi, 'startQueue').mockResolvedValue({
      data: { queuedCount: 0, jobIds: [] },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: { items: [], page: 0, size: 20, totalElements: 0, totalPages: 1 },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    const result = await store.startQueue();

    expect(result.queuedCount).toBe(0);
    const state = store.getState();
    expect(state.feedback?.type).toBe('info');
    expect(state.feedback?.message).toContain(
      'No hay descargas pendientes por encolar'
    );
  });

  it('startQueue blocks double-clicks while operating', async () => {
    const startMock = vi
      .spyOn(downloadsApi, 'startQueue')
      .mockImplementation(async () => {
        // Simulate in-flight delay
        await new Promise((r) => setTimeout(r, 50));
        return {
          data: { queuedCount: 1, jobIds: ['j-1'] },
          meta: { status: 202, statusText: 'Accepted' },
        } as never;
      });

    vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: { items: [], page: 0, size: 20, totalElements: 1, totalPages: 1 },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    // Call twice concurrently
    const p1 = store.startQueue();
    const p2 = store.startQueue();

    const [r1, r2] = await Promise.all([p1, p2]);

    expect(startMock).toHaveBeenCalledTimes(1);
    expect(r1.queuedCount).toBe(1);
    expect(r2.queuedCount).toBe(0); // Guarded
  });

  it('cancelQueue cancels all active work and refreshes view', async () => {
    vi.spyOn(downloadsApi, 'cancelQueue').mockResolvedValue({
      data: { cancelledCount: 2, cancellationRequestedCount: 1 },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: { items: [], page: 0, size: 20, totalElements: 3, totalPages: 1 },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    const result = await store.cancelQueue();

    expect(result.cancelledCount).toBe(2);
    expect(result.cancellationRequestedCount).toBe(1);
    const state = store.getState();
    expect(state.feedback?.message).toContain('Cola cancelada: 2 detenida(s)');
  });

  it('cancelDownload handles 200 immediate and 202 async cancellation', async () => {
    const cancelMock = vi
      .spyOn(downloadsApi, 'cancelDownload')
      .mockResolvedValueOnce({
        data: {
          id: 'job-1',
          status: 'CANCELLED',
          cancellationRequested: false,
        },
        meta: { status: 200, statusText: 'OK' },
      } as never)
      .mockResolvedValueOnce({
        data: {
          id: 'job-2',
          status: 'DOWNLOADING',
          cancellationRequested: true,
        },
        meta: { status: 202, statusText: 'Accepted' },
      } as never);

    vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: { items: [], page: 0, size: 20, totalElements: 0, totalPages: 1 },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    // Immediate cancellation
    const r1 = await store.cancelDownload('job-1');
    expect(r1.status).toBe('CANCELLED');
    expect(store.getState().feedback?.message).toContain(
      'Descarga cancelada correctamente'
    );

    // Asynchronous cancellation requested
    const r2 = await store.cancelDownload('job-2');
    expect(r2.cancellationRequested).toBe(true);
    expect(store.getState().feedback?.message).toContain(
      'Cancelación solicitada al worker'
    );

    expect(cancelMock).toHaveBeenCalledTimes(2);
  });

  it('retryDownload BLOCKS non-FAILED jobs on client to prevent 409 DOWNLOAD_NOT_RETRYABLE', async () => {
    // Populate store with a PENDING job
    vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: {
        items: [
          {
            id: 'job-pending',
            sourceUrl: 'https://yt.com/1',
            status: 'PENDING',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    await store.loadPage(0);

    const retryMock = vi.spyOn(downloadsApi, 'retryDownload');

    // Attempting to retry a PENDING job must be rejected client-side
    await expect(store.retryDownload('job-pending')).rejects.toThrow(
      /no puede ser reintentada/
    );

    // API must NEVER have been called!
    expect(retryMock).not.toHaveBeenCalled();
    expect(store.getState().error).toContain('no puede ser reintentada');
  });

  it('retryDownload allows FAILED jobs and returns them to PENDING', async () => {
    // Populate store with a FAILED job
    vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: {
        items: [
          {
            id: 'job-failed',
            sourceUrl: 'https://yt.com/1',
            status: 'FAILED',
            error: { code: 'HTTP_404', message: 'Not found' },
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    await store.loadPage(0);

    const retryMock = vi
      .spyOn(downloadsApi, 'retryDownload')
      .mockResolvedValue({
        data: {
          id: 'job-failed',
          status: 'PENDING',
          cancellationRequested: false,
        },
        meta: { status: 200, statusText: 'OK' },
      } as never);

    const result = await store.retryDownload('job-failed');

    expect(retryMock).toHaveBeenCalledWith('job-failed');
    expect(result.status).toBe('PENDING');
    expect(store.getState().feedback?.message).toContain(
      'Descarga restablecida a Pendiente'
    );
  });

  it('clearCompleted removes completed jobs and refreshes view', async () => {
    vi.spyOn(downloadsApi, 'clearCompletedQueue').mockResolvedValue({
      data: {
        clearedCount: 4,
        cancelledCount: 0,
        cancellationRequestedCount: 0,
      },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: { items: [], page: 0, size: 20, totalElements: 0, totalPages: 1 },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    const result = await store.clearCompleted();

    expect(result.clearedCount).toBe(4);
    expect(store.getState().feedback?.message).toContain(
      '4 descarga(s) completada(s) eliminada(s)'
    );
  });

  it('clearAll empties the visible queue and navigates to page 0', async () => {
    vi.spyOn(downloadsApi, 'clearQueue').mockResolvedValue({
      data: {
        clearedCount: 10,
        cancelledCount: 2,
        cancellationRequestedCount: 1,
      },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    const getMock = vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: { items: [], page: 0, size: 20, totalElements: 0, totalPages: 1 },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    const result = await store.clearAll();

    expect(result.clearedCount).toBe(10);
    expect(getMock).toHaveBeenCalledWith({ page: 0, size: 20 });
    expect(store.getState().feedback?.message).toContain(
      'Bandeja de descargas vaciada'
    );
  });

  it('handles 429 Rate Limit error with Retry-After header', async () => {
    const rateLimitError = new ApiProblemError(
      {
        type: 'about:blank',
        title: 'Too Many Requests',
        status: 429,
        detail: 'Demasiadas solicitudes por minuto.',
        code: 'RATE_LIMIT_EXCEEDED',
      },
      { retryAfterSeconds: 30 }
    );

    vi.spyOn(downloadsApi, 'startQueue').mockRejectedValue(rateLimitError);

    await expect(store.startQueue()).rejects.toThrow();

    const state = store.getState();
    expect(state.retryAfterSeconds).toBe(30);
    expect(state.error).toContain('30 segundos');
    expect(state.isOperating).toBe(false);
  });

  /* SSE and realtime event synchronization tests. */

  it('setSseStatus updates connection status in store state', () => {
    store.setSseStatus('connected');
    expect(store.getState().sseStatus).toBe('connected');

    store.setSseStatus('reconnecting');
    expect(store.getState().sseStatus).toBe('reconnecting');
  });

  it('applyRealtimeUpdate updates existing item in-place without tearing down others', async () => {
    // Seed initial state
    vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: {
        items: [
          {
            id: 'job-realtime-1',
            sourceUrl: 'https://youtube.com/watch?v=1',
            status: 'QUEUED',
            progress: 0,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    await store.loadPage(0);

    // Apply DOWNLOADING progress update
    store.applyRealtimeUpdate({
      eventId: '1720000000000-1',
      jobId: 'job-realtime-1',
      status: 'DOWNLOADING',
      progress: 42,
      speed: '3.5MiB/s',
      eta: '00:10',
      title: 'Resolved Song Title',
      errorCode: null,
      timestamp: '2026-10-14T02:00:00Z',
    });

    const item = store.getState().items[0];
    expect(item.status).toBe('DOWNLOADING');
    expect(item.progress).toBe(42);
    expect(item.speed).toBe('3.5MiB/s');
    expect(item.eta).toBe('00:10');
    expect(item.displayTitle).toBe('Resolved Song Title');
    expect(item.isTitleFallback).toBe(false);
    expect(item.canCancel).toBe(true);
    expect(item.canRetry).toBe(false);

    // Apply COMPLETED update
    store.applyRealtimeUpdate({
      eventId: '1720000000000-2',
      jobId: 'job-realtime-1',
      status: 'COMPLETED',
      progress: 100,
      speed: null,
      eta: null,
      title: null,
      errorCode: null,
      timestamp: '2026-10-14T02:01:00Z',
    });

    const completed = store.getState().items[0];
    expect(completed.status).toBe('COMPLETED');
    expect(completed.progress).toBe(100);
    expect(completed.canCancel).toBe(false);
    expect(completed.canRetry).toBe(false);
  });

  it('handleStreamReset immediately synchronizes authoritative state via REST query', async () => {
    const getMock = vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: {
        items: [
          {
            id: 'job-rest-sync',
            sourceUrl: 'https://youtube.com/watch?v=sync',
            status: 'COMPLETED',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    await store.handleStreamReset({ code: 'EVENT_HISTORY_EXPIRED' });

    expect(getMock).toHaveBeenCalled();
    const state = store.getState();
    expect(state.items.length).toBe(1);
    expect(state.items[0].id).toBe('job-rest-sync');
    expect(state.feedback?.message).toContain(
      'Historial de eventos expirado en el servidor'
    );
  });

  it('downloadArtifact delivers MP3 via saveBlobToDisk and marks as downloaded (ADR-0006)', async () => {
    vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: {
        items: [
          {
            id: 'job-artifact-1',
            sourceUrl: 'https://youtube.com/watch?v=1',
            status: 'COMPLETED',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    await store.loadPage(0);

    const retrieveMock = vi
      .spyOn(artifactManager, 'retrieveArtifact')
      .mockResolvedValue({
        blob: new Blob(['mp3-audio-data'], { type: 'audio/mpeg' }),
        filename: 'song.mp3',
        meta: { status: 200, statusText: 'OK' },
      });
    const saveMock = vi
      .spyOn(artifactManager, 'saveBlobToDisk')
      .mockImplementation(() => {});

    await store.downloadArtifact('job-artifact-1');

    expect(retrieveMock).toHaveBeenCalledWith('job-artifact-1');
    expect(saveMock).toHaveBeenCalledWith(expect.any(Blob), 'song.mp3');

    const state = store.getState();
    expect(state.artifactStatuses['job-artifact-1']).toBe('downloaded');
    expect(state.items[0].artifactStatus).toBe('downloaded');
    expect(state.items[0].canDownloadArtifact).toBe(false);
    expect(state.feedback?.type).toBe('success');
  });

  it('downloadArtifact handles 409 ARTIFACT_DELIVERY_IN_PROGRESS with retryable warning', async () => {
    vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: {
        items: [
          {
            id: 'job-artifact-409',
            sourceUrl: 'https://youtube.com/watch?v=1',
            status: 'COMPLETED',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    await store.loadPage(0);

    vi.spyOn(artifactManager, 'retrieveArtifact').mockRejectedValue(
      new ApiProblemError({
        type: 'https://musicwave.io/errors/artifact-conflict',
        title: 'Artifact Delivery In Progress',
        status: 409,
        detail: 'La descarga ya está en curso.',
        code: 'ARTIFACT_DELIVERY_IN_PROGRESS',
      })
    );

    await expect(
      store.downloadArtifact('job-artifact-409', { isAutomatic: false })
    ).rejects.toThrow();

    const state = store.getState();
    expect(state.artifactStatuses['job-artifact-409']).toBe('delivering');
    expect(state.feedback?.type).toBe('warning');
    expect(state.feedback?.message).toContain('ya se encuentra en curso');
  });

  it('downloadArtifact handles 410 ARTIFACT_ALREADY_DOWNLOADED without allowing retry', async () => {
    vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: {
        items: [
          {
            id: 'job-artifact-410',
            sourceUrl: 'https://youtube.com/watch?v=1',
            status: 'COMPLETED',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    await store.loadPage(0);

    vi.spyOn(artifactManager, 'retrieveArtifact').mockRejectedValue(
      new ApiProblemError({
        type: 'https://musicwave.io/errors/artifact-already-downloaded',
        title: 'Artifact Already Downloaded',
        status: 410,
        detail: 'El artefacto ya fue descargado previamente.',
        code: 'ARTIFACT_ALREADY_DOWNLOADED',
      })
    );

    await expect(
      store.downloadArtifact('job-artifact-410', { isAutomatic: false })
    ).rejects.toThrow();

    const state = store.getState();
    expect(state.artifactStatuses['job-artifact-410']).toBe('downloaded');
    expect(state.items[0].artifactStatus).toBe('downloaded');
    expect(state.items[0].canDownloadArtifact).toBe(false);
    expect(state.feedback?.type).toBe('error');
    expect(state.feedback?.message).toContain('un solo uso según ADR-0006');
  });

  it('downloadArtifact handles 410 ARTIFACT_EXPIRED marking it as expired', async () => {
    vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: {
        items: [
          {
            id: 'job-artifact-expired',
            sourceUrl: 'https://youtube.com/watch?v=1',
            status: 'COMPLETED',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    await store.loadPage(0);

    vi.spyOn(artifactManager, 'retrieveArtifact').mockRejectedValue(
      new ApiProblemError({
        type: 'https://musicwave.io/errors/artifact-expired',
        title: 'Artifact Expired',
        status: 410,
        detail: 'El artefacto ha expirado.',
        code: 'ARTIFACT_EXPIRED',
      })
    );

    await expect(
      store.downloadArtifact('job-artifact-expired', { isAutomatic: false })
    ).rejects.toThrow();

    const state = store.getState();
    expect(state.artifactStatuses['job-artifact-expired']).toBe('expired');
    expect(state.items[0].artifactStatus).toBe('expired');
    expect(state.items[0].canDownloadArtifact).toBe(false);
    expect(state.feedback?.message).toContain('ha expirado');
  });

  it('autoDownloadIfCompleted ensures one-shot dispatch idempotency per completed job', async () => {
    vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: {
        items: [
          {
            id: 'job-auto-1',
            sourceUrl: 'https://youtube.com/watch?v=1',
            status: 'COMPLETED',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    await store.loadPage(0);

    const retrieveMock = vi
      .spyOn(artifactManager, 'retrieveArtifact')
      .mockResolvedValue({
        blob: new Blob(['audio']),
        filename: 'song.mp3',
        meta: { status: 200, statusText: 'OK' },
      });
    vi.spyOn(artifactManager, 'saveBlobToDisk').mockImplementation(() => {});

    // First auto-download call
    store.autoDownloadIfCompleted('job-auto-1');
    expect(retrieveMock).toHaveBeenCalledTimes(1);

    // Second auto-download call must NOT trigger retrieveArtifact again
    store.autoDownloadIfCompleted('job-auto-1');
    expect(retrieveMock).toHaveBeenCalledTimes(1);
  });

  it('ADR-0006 storage invariant: MP3 blob is never stored in browser storage APIs', async () => {
    const mockStorage = {
      setItem: vi.fn(),
      getItem: vi.fn(),
      removeItem: vi.fn(),
      clear: vi.fn(),
    };
    Object.defineProperty(globalThis, 'localStorage', {
      value: mockStorage,
      configurable: true,
      writable: true,
    });
    Object.defineProperty(globalThis, 'sessionStorage', {
      value: mockStorage,
      configurable: true,
      writable: true,
    });

    vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: {
        items: [
          {
            id: 'job-storage-check',
            sourceUrl: 'https://youtube.com/watch?v=1',
            status: 'COMPLETED',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    await store.loadPage(0);

    vi.spyOn(artifactManager, 'retrieveArtifact').mockResolvedValue({
      blob: new Blob(['audio']),
      filename: 'song.mp3',
      meta: { status: 200, statusText: 'OK' },
    });
    vi.spyOn(artifactManager, 'saveBlobToDisk').mockImplementation(() => {});

    await store.downloadArtifact('job-storage-check');

    // Verify localStorage & sessionStorage do not hold audio or artifact blobs
    expect(mockStorage.setItem).not.toHaveBeenCalled();
  });
});
