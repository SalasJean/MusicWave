import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { SettingsStore } from './settingsStore';
import * as settingsApi from '../api/settingsApi';
import { ApiProblemError, NetworkError } from '@/shared/api/problemDetails';

describe('SettingsStore', () => {
  let store: SettingsStore;

  beforeEach(() => {
    vi.restoreAllMocks();
    store = new SettingsStore();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('loadSettings fetches settings and updates store state', async () => {
    const mockResponse = {
      data: {
        maxConcurrency: 5,
        audioQuality: '320K',
      },
      meta: { status: 200, statusText: 'OK' },
    };

    vi.spyOn(settingsApi, 'getSettings').mockResolvedValue(
      mockResponse as never
    );

    const listener = vi.fn();
    store.addEventListener('stateChange', listener);

    await store.loadSettings();

    const state = store.getState();
    expect(state.loading).toBe(false);
    expect(state.settings.maxConcurrency).toBe(5);
    expect(state.settings.audioQuality).toBe('320K');
    expect(state.initialSettings.maxConcurrency).toBe(5);
    expect(state.initialSettings.audioQuality).toBe('320K');
    expect(state.isDirty).toBe(false);
    expect(state.error).toBeNull();
    expect(listener).toHaveBeenCalled();
  });

  it('loadSettings handles ApiProblemError gracefully', async () => {
    const problem = new ApiProblemError({
      type: 'about:blank',
      title: 'Internal Server Error',
      status: 500,
      detail: 'Error en la base de datos de ajustes.',
      code: 'INTERNAL_SERVER_ERROR',
    });

    vi.spyOn(settingsApi, 'getSettings').mockRejectedValue(problem);

    await store.loadSettings();

    const state = store.getState();
    expect(state.loading).toBe(false);
    expect(state.error).toBe('Error en la base de datos de ajustes.');
  });

  it('updateField updates draft and toggles isDirty', () => {
    expect(store.getState().isDirty).toBe(false);

    store.updateField('maxConcurrency', 8);

    let state = store.getState();
    expect(state.settings.maxConcurrency).toBe(8);
    expect(state.isDirty).toBe(true);

    // Revert back to original value (3)
    store.updateField('maxConcurrency', 3);
    state = store.getState();
    expect(state.isDirty).toBe(false);
  });

  it('resetToInitial reverts changes back to initial state', () => {
    store.updateField('maxConcurrency', 9);
    store.updateField('audioQuality', '64K');
    expect(store.getState().isDirty).toBe(true);

    store.resetToInitial();

    const state = store.getState();
    expect(state.settings.maxConcurrency).toBe(
      state.initialSettings.maxConcurrency
    );
    expect(state.settings.audioQuality).toBe(
      state.initialSettings.audioQuality
    );
    expect(state.isDirty).toBe(false);
    expect(state.fieldErrors).toEqual({});
  });

  it('saveSettings aborts on client-side validation failure', async () => {
    const updateSpy = vi.spyOn(settingsApi, 'updateSettings');

    // Set invalid maxConcurrency
    store.updateField('maxConcurrency', 99);

    const result = await store.saveSettings();

    expect(result).toBe(false);
    expect(updateSpy).not.toHaveBeenCalled();

    const state = store.getState();
    expect(state.fieldErrors.maxConcurrency).toBeDefined();
    expect(state.error).toContain('corrija los errores');
    expect(state.saving).toBe(false);
  });

  it('saveSettings submits valid payload, syncs initialSettings, and shows success message', async () => {
    vi.spyOn(settingsApi, 'updateSettings').mockResolvedValue({
      data: {
        maxConcurrency: 7,
        audioQuality: '256K',
      },
      meta: { status: 200, statusText: 'OK' },
    } as never);

    store.updateField('maxConcurrency', 7);
    store.updateField('audioQuality', '256K');

    const result = await store.saveSettings();

    expect(result).toBe(true);
    const state = store.getState();
    expect(state.saving).toBe(false);
    expect(state.isDirty).toBe(false);
    expect(state.initialSettings.maxConcurrency).toBe(7);
    expect(state.initialSettings.audioQuality).toBe('256K');
    expect(state.successMessage).toContain('guardados correctamente');
    expect(state.error).toBeNull();
  });

  it('saveSettings handles backend 400 validation problem error and maps fields', async () => {
    const problem = new ApiProblemError({
      type: 'https://musicwave.io/errors/invalid-settings',
      title: 'Invalid Settings',
      status: 400,
      detail: 'maxConcurrency must be between 1 and 10',
      code: 'SETTINGS_MAX_CONCURRENCY_INVALID',
    });

    vi.spyOn(settingsApi, 'updateSettings').mockRejectedValue(problem);

    store.updateField('maxConcurrency', 2);

    const result = await store.saveSettings();

    expect(result).toBe(false);
    const state = store.getState();
    expect(state.saving).toBe(false);
    expect(state.fieldErrors.maxConcurrency).toBeDefined();
    expect(state.fieldErrors.maxConcurrency).toContain('entre 1 y 10');
  });

  it('saveSettings handles NetworkError gracefully', async () => {
    vi.spyOn(settingsApi, 'updateSettings').mockRejectedValue(
      new NetworkError('Fallo de conexión al servidor')
    );

    store.updateField('maxConcurrency', 4);

    const result = await store.saveSettings();

    expect(result).toBe(false);
    const state = store.getState();
    expect(state.saving).toBe(false);
    expect(state.error).toBe('Fallo de conexión al servidor');
  });
});
