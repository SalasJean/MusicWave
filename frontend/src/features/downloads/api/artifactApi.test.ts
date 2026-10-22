import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { getArtifactBlob, extractFilenameFromHeader } from './artifactApi';

describe('artifactApi adapter', () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it('extractFilenameFromHeader parses standard and UTF-8 RFC 5987 headers', () => {
    expect(extractFilenameFromHeader('attachment; filename="song.mp3"')).toBe(
      'song.mp3'
    );

    expect(
      extractFilenameFromHeader("attachment; filename*=UTF-8''Canci%C3%B3n.mp3")
    ).toBe('Canción.mp3');

    expect(extractFilenameFromHeader(undefined, 'fallback.mp3')).toBe(
      'fallback.mp3'
    );
  });

  it('getArtifactBlob requests audio/mpeg blob from /downloads/{id}/artifact', async () => {
    const mockBlob = new Blob(['fake mp3 content'], { type: 'audio/mpeg' });

    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(mockBlob, {
        status: 200,
        headers: new Headers({
          'Content-Type': 'audio/mpeg',
          'Content-Disposition': 'attachment; filename="Rick_Astley.mp3"',
          'X-Request-ID': 'req-artifact-1',
        }),
      })
    );

    const result = await getArtifactBlob('job-123');

    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/downloads/job-123/artifact',
      expect.objectContaining({
        method: 'GET',
        headers: expect.any(Headers),
      })
    );

    expect(result.filename).toBe('Rick_Astley.mp3');
    expect(result.blob).toBeDefined();
    expect(result.meta.requestId).toBe('req-artifact-1');
  });
});
