import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { downloadStore } from '../stores/downloadStore';
import { settingsStore } from '@/features/settings/stores/settingsStore';
import * as downloadsApi from '../api/downloadsApi';
import * as settingsApi from '@/features/settings/api/settingsApi';
import * as artifactManager from '../lib/artifactManager';

describe('Integration Flow: End-to-End User Journeys', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('Journey 1: Ingestion -> Queue Start -> Realtime Progress -> MP3 One-Shot Delivery', async () => {
    // 1. Ingest Single URL with Idempotency-Key
    const createDownloadSpy = vi
      .spyOn(downloadsApi, 'createDownload')
      .mockResolvedValue({
        data: {
          requestId: 'req-e2e-1',
          replayed: false,
          createdCount: 1,
          rejectedCount: 0,
          jobs: [
            {
              id: 'job-e2e-1',
              sourceUrl: 'https://youtube.com/watch?v=e2e',
              status: 'PENDING',
            },
          ],
          rejected: [],
        },
        meta: { status: 201, statusText: 'Created' },
      } as never);

    vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: {
        items: [
          {
            id: 'job-e2e-1',
            sourceUrl: 'https://youtube.com/watch?v=e2e',
            status: 'PENDING',
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

    const summary = await downloadStore.addSingle(
      'https://youtube.com/watch?v=e2e'
    );
    expect(createDownloadSpy).toHaveBeenCalledWith(
      { url: 'https://youtube.com/watch?v=e2e' },
      expect.objectContaining({
        idempotencyKey: expect.stringMatching(/^single-/),
      })
    );
    expect(summary.createdCount).toBe(1);

    // Verify initial store state
    let state = downloadStore.getState();
    expect(state.items[0].status).toBe('PENDING');

    // 2. Start the Queue
    const startQueueSpy = vi
      .spyOn(downloadsApi, 'startQueue')
      .mockResolvedValue({
        data: { queuedCount: 1, jobIds: ['job-e2e-1'] },
        meta: { status: 202, statusText: 'Accepted' },
      } as never);

    await downloadStore.startQueue();
    expect(startQueueSpy).toHaveBeenCalled();

    // 3. Receive Real-time SSE Events
    // Event A: DOWNLOADING with progress
    downloadStore.applyRealtimeUpdate({
      eventId: '1720000000000-1',
      jobId: 'job-e2e-1',
      status: 'DOWNLOADING',
      progress: 45,
      speed: '4.2MiB/s',
      eta: '00:08',
      title: 'E2E Song Title',
      errorCode: null,
      timestamp: '2026-10-14T22:00:00Z',
    });

    state = downloadStore.getState();
    expect(state.items[0].status).toBe('DOWNLOADING');
    expect(state.items[0].progress).toBe(45);
    expect(state.items[0].speed).toBe('4.2MiB/s');
    expect(state.items[0].displayTitle).toBe('E2E Song Title');

    // Event B: CONVERTING
    downloadStore.applyRealtimeUpdate({
      eventId: '1720000000000-2',
      jobId: 'job-e2e-1',
      status: 'CONVERTING',
      progress: 90,
      speed: null,
      eta: null,
      title: 'E2E Song Title',
      errorCode: null,
      timestamp: '2026-10-14T22:00:10Z',
    });

    state = downloadStore.getState();
    expect(state.items[0].status).toBe('CONVERTING');
    expect(state.items[0].progress).toBe(90);

    // Event C: COMPLETED (triggers automatic artifact download via autoDownloadIfCompleted)
    const retrieveMock = vi
      .spyOn(artifactManager, 'retrieveArtifact')
      .mockResolvedValue({
        blob: new Blob(['audio-binary'], { type: 'audio/mpeg' }),
        filename: 'e2e-song.mp3',
        meta: { status: 200, statusText: 'OK' },
      });
    const saveMock = vi
      .spyOn(artifactManager, 'saveBlobToDisk')
      .mockImplementation(() => {});

    downloadStore.applyRealtimeUpdate({
      eventId: '1720000000000-3',
      jobId: 'job-e2e-1',
      status: 'COMPLETED',
      progress: 100,
      speed: null,
      eta: null,
      title: 'E2E Song Title',
      errorCode: null,
      timestamp: '2026-10-14T22:00:15Z',
    });

    // Wait for async auto-download execution
    await new Promise((resolve) => setTimeout(resolve, 20));

    // 4. Verify One-Shot Artifact Delivery
    expect(retrieveMock).toHaveBeenCalledWith('job-e2e-1');
    expect(saveMock).toHaveBeenCalledWith(expect.any(Blob), 'e2e-song.mp3');

    state = downloadStore.getState();
    expect(state.items[0].status).toBe('COMPLETED');
    expect(state.items[0].artifactStatus).toBe('downloaded');
    expect(state.items[0].canDownloadArtifact).toBe(false);

    // 5. Subsequent delivery attempts are blocked by ADR-0006
    downloadStore.autoDownloadIfCompleted('job-e2e-1');
    expect(retrieveMock).toHaveBeenCalledTimes(1);
  });

  it('Journey 2: Bulk Partial Acceptance -> Error Reporting -> Individual Retry', async () => {
    // 1. Submit Bulk with Partial Rejection
    vi.spyOn(downloadsApi, 'createBulkDownloads').mockResolvedValue({
      data: {
        requestId: 'req-bulk-e2e',
        replayed: false,
        createdCount: 1,
        rejectedCount: 1,
        jobs: [
          {
            id: 'job-valid-1',
            sourceUrl: 'https://youtube.com/watch?v=valid',
            status: 'PENDING',
          },
        ],
        rejected: [
          {
            position: 2,
            value: 'invalid-url',
            code: 'URL_INVALID',
            message: 'Unsupported host',
          },
        ],
      },
      meta: { status: 201, statusText: 'Created' },
    } as never);

    vi.spyOn(downloadsApi, 'getDownloads').mockResolvedValue({
      data: {
        items: [
          {
            id: 'job-valid-1',
            sourceUrl: 'https://youtube.com/watch?v=valid',
            status: 'FAILED',
            error: { code: 'HTTP_404', message: 'Video was made private' },
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    const summary = await downloadStore.addBulk([
      'https://youtube.com/watch?v=valid',
      'invalid-url',
    ]);

    expect(summary.createdCount).toBe(1);
    expect(summary.rejectedCount).toBe(1);
    expect(summary.rejectedItems[0].code).toBe('URL_INVALID');

    // 2. Retry the FAILED job
    const retrySpy = vi.spyOn(downloadsApi, 'retryDownload').mockResolvedValue({
      data: { message: 'Download retry accepted', status: 'PENDING' },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    await downloadStore.retryDownload('job-valid-1');
    expect(retrySpy).toHaveBeenCalledWith('job-valid-1');
  });

  it('Journey 3: Settings Lifecycle -> Load -> Edit Draft -> Validate -> Persist', async () => {
    // 1. Initial Load from Backend
    vi.spyOn(settingsApi, 'getSettings').mockResolvedValue({
      data: { maxConcurrency: 3, audioQuality: '192K' },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    await settingsStore.loadSettings();
    let state = settingsStore.getState();
    expect(state.settings.maxConcurrency).toBe(3);
    expect(state.settings.audioQuality).toBe('192K');
    expect(state.isDirty).toBe(false);

    // 2. Edit draft with out-of-range value (validation gate)
    settingsStore.updateField('maxConcurrency', 15);
    state = settingsStore.getState();
    expect(state.isDirty).toBe(true);

    const saveFailed = await settingsStore.saveSettings();
    expect(saveFailed).toBe(false);
    expect(settingsStore.getState().fieldErrors.maxConcurrency).toBeDefined();

    // 3. Edit draft with valid values and save
    const updateSpy = vi
      .spyOn(settingsApi, 'updateSettings')
      .mockResolvedValue({
        data: { maxConcurrency: 8, audioQuality: '320K' },
        meta: { status: 200, statusText: 'OK' },
      } as never);

    settingsStore.updateField('maxConcurrency', 8);
    settingsStore.updateField('audioQuality', '320K');

    const saveSuccess = await settingsStore.saveSettings();
    expect(saveSuccess).toBe(true);
    expect(updateSpy).toHaveBeenCalledWith({
      maxConcurrency: 8,
      audioQuality: '320K',
    });

    state = settingsStore.getState();
    expect(state.isDirty).toBe(false);
    expect(state.initialSettings.maxConcurrency).toBe(8);
    expect(state.initialSettings.audioQuality).toBe('320K');
    expect(state.successMessage).toContain('guardados correctamente');
  });
});
