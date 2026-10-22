export const AUDIO_QUALITY_OPTIONS = [
  { value: '64K', label: '64 kbps (Baja fidelidad, tamaño mínimo)' },
  { value: '96K', label: '96 kbps (Calidad voz / radio AM)' },
  { value: '128K', label: '128 kbps (Estándar básico web)' },
  { value: '160K', label: '160 kbps (Fidelidad media aceptable)' },
  {
    value: '192K',
    label: '192 kbps (Recomendada — equilibrio tamaño/calidad)',
    isDefault: true,
  },
  { value: '256K', label: '256 kbps (Alta fidelidad para audiófilos)' },
  { value: '320K', label: '320 kbps (Máxima calidad MP3 disponible)' },
] as const;

export type AudioQuality = (typeof AUDIO_QUALITY_OPTIONS)[number]['value'];

export const MIN_CONCURRENCY = 1;
export const MAX_CONCURRENCY = 10;
export const DEFAULT_CONCURRENCY = 3;
export const DEFAULT_AUDIO_QUALITY: AudioQuality = '192K';

export interface SettingsValidationResult {
  valid: boolean;
  errors: {
    maxConcurrency?: string;
    audioQuality?: string;
  };
}

/**
 * Validates concurrency value (integer between 1 and 10 inclusive).
 */
export function validateConcurrency(value: unknown): {
  valid: boolean;
  error?: string;
} {
  const num = typeof value === 'string' ? Number(value) : value;

  if (typeof num !== 'number' || Number.isNaN(num)) {
    return {
      valid: false,
      error: 'La concurrencia debe ser un número válido.',
    };
  }

  if (!Number.isInteger(num)) {
    return {
      valid: false,
      error: 'La concurrencia debe ser un número entero sin decimales.',
    };
  }

  if (num < MIN_CONCURRENCY || num > MAX_CONCURRENCY) {
    return {
      valid: false,
      error: `La concurrencia debe estar comprendida entre ${MIN_CONCURRENCY} y ${MAX_CONCURRENCY} descargas simultáneas.`,
    };
  }

  return { valid: true };
}

/**
 * Validates audio quality selection against allowed values.
 */
export function validateAudioQuality(value: unknown): {
  valid: boolean;
  error?: string;
} {
  if (typeof value !== 'string' || !value.trim()) {
    return {
      valid: false,
      error: 'Debe seleccionar una calidad de audio.',
    };
  }

  const normalized = value.trim().toUpperCase();
  const isValid = AUDIO_QUALITY_OPTIONS.some(
    (opt) => opt.value.toUpperCase() === normalized
  );

  if (!isValid) {
    return {
      valid: false,
      error:
        'Calidad de audio no admitida. Elija entre 64K, 96K, 128K, 160K, 192K, 256K o 320K.',
    };
  }

  return { valid: true };
}

/**
 * Validates an entire settings payload.
 */
export function validateSettings(settings: {
  maxConcurrency: unknown;
  audioQuality: unknown;
}): SettingsValidationResult {
  const concurrencyResult = validateConcurrency(settings.maxConcurrency);
  const audioResult = validateAudioQuality(settings.audioQuality);

  const errors: { maxConcurrency?: string; audioQuality?: string } = {};

  if (!concurrencyResult.valid && concurrencyResult.error) {
    errors.maxConcurrency = concurrencyResult.error;
  }

  if (!audioResult.valid && audioResult.error) {
    errors.audioQuality = audioResult.error;
  }

  return {
    valid: concurrencyResult.valid && audioResult.valid,
    errors,
  };
}

/**
 * Translates backend problem details error codes to user-friendly messages.
 */
export function mapBackendSettingsError(
  code?: string,
  detail?: string
): { field?: 'maxConcurrency' | 'audioQuality'; message: string } {
  if (code === 'SETTINGS_MAX_CONCURRENCY_INVALID') {
    return {
      field: 'maxConcurrency',
      message: `El límite de concurrencia debe estar comprendido entre ${MIN_CONCURRENCY} y ${MAX_CONCURRENCY} descargas simultáneas.`,
    };
  }

  if (code === 'SETTINGS_AUDIO_QUALITY_INVALID') {
    return {
      field: 'audioQuality',
      message:
        'La calidad de audio debe ser una de las opciones permitidas (64K, 96K, 128K, 160K, 192K, 256K, 320K).',
    };
  }

  return {
    message: detail || 'Error al actualizar los ajustes en el servidor.',
  };
}
