import { describe, it, expect } from 'vitest';
import {
  formatFallbackTitle,
  resolveDisplayTitle,
  formatDate,
  formatJobError,
} from './formatters';

describe('download formatters', () => {
  describe('formatFallbackTitle', () => {
    it('extracts YouTube video ID when present', () => {
      const title = formatFallbackTitle(
        'https://www.youtube.com/watch?v=BaW_jenozKc'
      );
      expect(title).toBe('YouTube (BaW_jenozKc)');
    });

    it('extracts last path segment for generic URLs', () => {
      const title = formatFallbackTitle(
        'https://example.com/audio/my-song.mp3'
      );
      expect(title).toBe('example.com/my-song.mp3');
    });

    it('handles empty or invalid URLs gracefully', () => {
      expect(formatFallbackTitle(null)).toBe('Descarga sin título');
      expect(formatFallbackTitle('')).toBe('Descarga sin título');
      expect(formatFallbackTitle('not-a-url')).toBe('not-a-url');
    });
  });

  describe('resolveDisplayTitle', () => {
    it('returns resolved title when available and status is not PENDING', () => {
      const res = resolveDisplayTitle(
        'Rick Astley - Never Gonna Give You Up',
        'RESOLVED',
        'https://youtube.com/watch?v=123'
      );
      expect(res.title).toBe('Rick Astley - Never Gonna Give You Up');
      expect(res.isFallback).toBe(false);
    });

    it('uses fallback URL when titleStatus is PENDING', () => {
      const res = resolveDisplayTitle(
        'Some Old Title',
        'PENDING',
        'https://www.youtube.com/watch?v=BaW_jenozKc'
      );
      expect(res.title).toBe('YouTube (BaW_jenozKc)');
      expect(res.isFallback).toBe(true);
    });

    it('uses fallback URL when title is null or empty', () => {
      const res = resolveDisplayTitle(
        null,
        'RESOLVED',
        'https://www.youtube.com/watch?v=BaW_jenozKc'
      );
      expect(res.title).toBe('YouTube (BaW_jenozKc)');
      expect(res.isFallback).toBe(true);
    });
  });

  describe('formatDate', () => {
    it('formats ISO dates accurately', () => {
      const formatted = formatDate('2026-10-14T02:00:00Z');
      expect(formatted).not.toBe('—');
      expect(typeof formatted).toBe('string');
    });

    it('returns dash for null/undefined date', () => {
      expect(formatDate(null)).toBe('—');
      expect(formatDate(undefined)).toBe('—');
    });
  });

  describe('formatJobError', () => {
    it('maps known MusicWave RFC 9457 error codes into friendly Spanish', () => {
      expect(formatJobError('URL_DESTINATION_NOT_ALLOWED')).toContain(
        'Destino no permitido'
      );
      expect(formatJobError('DOWNLOAD_QUOTA_EXCEEDED')).toContain(
        'Límite de descargas activas alcanzado'
      );
      expect(formatJobError('ARTIFACT_ALREADY_DOWNLOADED')).toContain(
        'ya fue descargado una vez'
      );
      expect(formatJobError('ARTIFACT_EXPIRED')).toContain('ha caducado');
    });

    it('falls back to message when code is not in dictionary', () => {
      expect(
        formatJobError('UNKNOWN_CODE', 'Custom worker error message')
      ).toBe('Custom worker error message');
    });

    it('handles empty code and message gracefully', () => {
      expect(formatJobError(null, null)).toContain('Error desconocido');
    });
  });
});
