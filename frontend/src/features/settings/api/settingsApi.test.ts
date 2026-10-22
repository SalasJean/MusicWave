import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { getSettings, updateSettings } from './settingsApi';

describe('settingsApi adapter', () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it('getSettings queries GET /api/v1/settings', async () => {
    const mockSettings = { maxConcurrency: 5, audioQuality: '192K' };

    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(mockSettings), {
        status: 200,
        headers: new Headers({ 'Content-Type': 'application/json' }),
      })
    );

    const result = await getSettings();

    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/settings',
      expect.objectContaining({ method: 'GET' })
    );
    expect(result.data).toEqual(mockSettings);
  });

  it('updateSettings sends PUT /api/v1/settings with payload', async () => {
    const updated = { maxConcurrency: 2, audioQuality: '128K' };

    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(updated), {
        status: 200,
        headers: new Headers({ 'Content-Type': 'application/json' }),
      })
    );

    const result = await updateSettings(updated);

    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/settings',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify(updated),
      })
    );
    expect(result.data).toEqual(updated);
  });
});
