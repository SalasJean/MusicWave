import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { handleArtifactError, saveBlobToDisk } from './artifactManager';
import { ApiProblemError, NetworkError } from '@/shared/api/problemDetails';

describe('artifactManager', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('handleArtifactError ADR-0006 compliance', () => {
    it('handles 410 ARTIFACT_ALREADY_DOWNLOADED (single-use consumed, prohibited to retry)', () => {
      const err = new ApiProblemError({
        type: 'about:blank',
        title: 'Gone',
        status: 410,
        detail: 'The requested artifact has already been downloaded.',
        code: 'ARTIFACT_ALREADY_DOWNLOADED',
      });

      const result = handleArtifactError(err);
      expect(result.status).toBe('downloaded');
      expect(result.canRetry).toBe(false);
      expect(result.message).toContain('ya fue descargado previamente');
    });

    it('handles 410 ARTIFACT_EXPIRED (1-hour TTL reached, prohibited to retry)', () => {
      const err = new ApiProblemError({
        type: 'about:blank',
        title: 'Gone',
        status: 410,
        detail: 'The requested artifact has expired.',
        code: 'ARTIFACT_EXPIRED',
      });

      const result = handleArtifactError(err);
      expect(result.status).toBe('expired');
      expect(result.canRetry).toBe(false);
      expect(result.message).toContain('ha expirado');
    });

    it('handles 410 ARTIFACT_MISSING (object missing, prohibited to retry)', () => {
      const err = new ApiProblemError({
        type: 'about:blank',
        title: 'Gone',
        status: 410,
        detail: 'Artifact missing from object storage.',
        code: 'ARTIFACT_MISSING',
      });

      const result = handleArtifactError(err);
      expect(result.status).toBe('missing');
      expect(result.canRetry).toBe(false);
      expect(result.message).toContain('no se encuentra disponible');
    });

    it('handles 409 ARTIFACT_DELIVERY_IN_PROGRESS', () => {
      const err = new ApiProblemError({
        type: 'about:blank',
        title: 'Conflict',
        status: 409,
        detail: 'Artifact delivery lease currently active.',
        code: 'ARTIFACT_DELIVERY_IN_PROGRESS',
      });

      const result = handleArtifactError(err);
      expect(result.status).toBe('delivering');
      expect(result.canRetry).toBe(true);
      expect(result.message).toContain('ya se encuentra en curso');
    });

    it('handles 409 ARTIFACT_NOT_READY', () => {
      const err = new ApiProblemError({
        type: 'about:blank',
        title: 'Conflict',
        status: 409,
        detail: 'Artifact not ready yet.',
        code: 'ARTIFACT_NOT_READY',
      });

      const result = handleArtifactError(err);
      expect(result.status).toBe('idle');
      expect(result.canRetry).toBe(true);
      expect(result.message).toContain('aún no ha finalizado su procesamiento');
    });

    it('handles 503 ARTIFACT_STORAGE_UNAVAILABLE', () => {
      const err = new ApiProblemError({
        type: 'about:blank',
        title: 'Service Unavailable',
        status: 503,
        detail: 'S3 storage unreachable.',
        code: 'ARTIFACT_STORAGE_UNAVAILABLE',
      });

      const result = handleArtifactError(err);
      expect(result.status).toBe('error');
      expect(result.canRetry).toBe(true);
      expect(result.message.toLowerCase()).toContain('almacenamiento temporal');
    });

    it('handles NetworkError', () => {
      const err = new NetworkError('Conexión perdida');
      const result = handleArtifactError(err);
      expect(result.status).toBe('error');
      expect(result.canRetry).toBe(true);
      expect(result.message).toBe('Conexión perdida');
    });
  });

  describe('saveBlobToDisk browser delivery', () => {
    it('creates object URL, triggers anchor click and cleans up without persistent storage', () => {
      const createObjectURLMock = vi
        .fn()
        .mockReturnValue('blob:http://localhost/test-uuid');
      const revokeObjectURLMock = vi.fn();
      globalThis.URL.createObjectURL = createObjectURLMock;
      globalThis.URL.revokeObjectURL = revokeObjectURLMock;

      const clickMock = vi.fn();
      const removeChildMock = vi.fn();
      const appendChildMock = vi.fn();

      const mockAnchor = {
        href: '',
        download: '',
        style: { display: '' },
        click: clickMock,
      };

      const mockDoc = {
        createElement: vi.fn().mockReturnValue(mockAnchor),
        body: {
          appendChild: appendChildMock,
          removeChild: removeChildMock,
        },
      };

      Object.defineProperty(globalThis, 'window', {
        value: {},
        configurable: true,
        writable: true,
      });
      Object.defineProperty(globalThis, 'document', {
        value: mockDoc,
        configurable: true,
        writable: true,
      });

      const blob = new Blob(['mp3-audio-bytes'], { type: 'audio/mpeg' });
      saveBlobToDisk(blob, 'song.mp3');

      expect(createObjectURLMock).toHaveBeenCalledWith(blob);
      expect(mockDoc.createElement).toHaveBeenCalledWith('a');
      expect(appendChildMock).toHaveBeenCalledWith(mockAnchor);
      expect(clickMock).toHaveBeenCalled();
      expect(mockAnchor.download).toBe('song.mp3');
      expect(mockAnchor.href).toBe('blob:http://localhost/test-uuid');
    });
  });
});
