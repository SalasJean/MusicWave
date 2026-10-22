import { describe, it, expect } from 'vitest';
import {
  validateConcurrency,
  validateAudioQuality,
  validateSettings,
  mapBackendSettingsError,
  AUDIO_QUALITY_OPTIONS,
} from './settingsModel';

describe('settingsModel validation', () => {
  describe('validateConcurrency', () => {
    it('accepts valid integers between 1 and 10', () => {
      expect(validateConcurrency(1).valid).toBe(true);
      expect(validateConcurrency(5).valid).toBe(true);
      expect(validateConcurrency(10).valid).toBe(true);
    });

    it('accepts valid numeric strings within range', () => {
      expect(validateConcurrency('3').valid).toBe(true);
      expect(validateConcurrency('10').valid).toBe(true);
    });

    it('rejects numbers lower than 1', () => {
      const res = validateConcurrency(0);
      expect(res.valid).toBe(false);
      expect(res.error).toContain('entre 1 y 10');

      const negative = validateConcurrency(-3);
      expect(negative.valid).toBe(false);
    });

    it('rejects numbers higher than 10', () => {
      const res = validateConcurrency(11);
      expect(res.valid).toBe(false);
      expect(res.error).toContain('entre 1 y 10');
    });

    it('rejects non-integer decimal numbers', () => {
      const res = validateConcurrency(3.5);
      expect(res.valid).toBe(false);
      expect(res.error).toContain('entero sin decimales');
    });

    it('rejects invalid non-numeric inputs', () => {
      expect(validateConcurrency(NaN).valid).toBe(false);
      expect(validateConcurrency('abc').valid).toBe(false);
      expect(validateConcurrency(undefined).valid).toBe(false);
      expect(validateConcurrency(null).valid).toBe(false);
    });
  });

  describe('validateAudioQuality', () => {
    it('accepts all officially supported audio qualities', () => {
      for (const opt of AUDIO_QUALITY_OPTIONS) {
        const res = validateAudioQuality(opt.value);
        expect(res.valid).toBe(true);
      }
    });

    it('accepts case-insensitive and trimmed quality values', () => {
      expect(validateAudioQuality('  192k  ').valid).toBe(true);
      expect(validateAudioQuality('320k').valid).toBe(true);
    });

    it('rejects unsupported quality strings', () => {
      const res = validateAudioQuality('FLAC');
      expect(res.valid).toBe(false);
      expect(res.error).toContain('Calidad de audio no admitida');

      expect(validateAudioQuality('500K').valid).toBe(false);
      expect(validateAudioQuality('192').valid).toBe(false);
    });

    it('rejects empty or non-string inputs', () => {
      expect(validateAudioQuality('').valid).toBe(false);
      expect(validateAudioQuality('   ').valid).toBe(false);
      expect(validateAudioQuality(null).valid).toBe(false);
      expect(validateAudioQuality(undefined).valid).toBe(false);
    });
  });

  describe('validateSettings', () => {
    it('returns valid true when both fields are correct', () => {
      const res = validateSettings({
        maxConcurrency: 3,
        audioQuality: '192K',
      });
      expect(res.valid).toBe(true);
      expect(Object.keys(res.errors)).toHaveLength(0);
    });

    it('returns valid false and records errors for both invalid fields', () => {
      const res = validateSettings({
        maxConcurrency: 20,
        audioQuality: 'INVALID',
      });
      expect(res.valid).toBe(false);
      expect(res.errors.maxConcurrency).toBeDefined();
      expect(res.errors.audioQuality).toBeDefined();
    });
  });

  describe('mapBackendSettingsError', () => {
    it('maps SETTINGS_MAX_CONCURRENCY_INVALID to maxConcurrency field', () => {
      const res = mapBackendSettingsError('SETTINGS_MAX_CONCURRENCY_INVALID');
      expect(res.field).toBe('maxConcurrency');
      expect(res.message).toContain('entre 1 y 10');
    });

    it('maps SETTINGS_AUDIO_QUALITY_INVALID to audioQuality field', () => {
      const res = mapBackendSettingsError('SETTINGS_AUDIO_QUALITY_INVALID');
      expect(res.field).toBe('audioQuality');
      expect(res.message).toContain('opciones permitidas');
    });

    it('falls back to generic message for unknown error code', () => {
      const res = mapBackendSettingsError('INTERNAL_ERROR', 'Detalle custom');
      expect(res.field).toBeUndefined();
      expect(res.message).toBe('Detalle custom');
    });
  });
});
