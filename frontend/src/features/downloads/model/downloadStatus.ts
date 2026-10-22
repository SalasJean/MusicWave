export type DownloadStatus =
  | 'PENDING'
  | 'QUEUED'
  | 'DOWNLOADING'
  | 'CONVERTING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

export interface StatusConfig {
  label: string;
  badgeVariant:
    | 'pending'
    | 'queued'
    | 'downloading'
    | 'converting'
    | 'completed'
    | 'failed'
    | 'cancelled';
  icon: string;
  description: string;
  isTerminal: boolean;
  isActive: boolean;
}

export const STATUS_CONFIG_MAP: Record<DownloadStatus, StatusConfig> = {
  PENDING: {
    label: 'Pendiente',
    badgeVariant: 'pending',
    icon: '⏳',
    description: 'En espera en la bandeja de entrada',
    isTerminal: false,
    isActive: false,
  },
  QUEUED: {
    label: 'En cola',
    badgeVariant: 'queued',
    icon: '🔵',
    description: 'Encolado en RabbitMQ para procesamiento por un worker',
    isTerminal: false,
    isActive: true,
  },
  DOWNLOADING: {
    label: 'Descargando',
    badgeVariant: 'downloading',
    icon: '⚡',
    description: 'yt-dlp extrayendo el flujo de audio de origen',
    isTerminal: false,
    isActive: true,
  },
  CONVERTING: {
    label: 'Convirtiendo',
    badgeVariant: 'converting',
    icon: '🔄',
    description:
      'FFmpeg transcodificando el audio a MP3 con calidad configurada',
    isTerminal: false,
    isActive: true,
  },
  COMPLETED: {
    label: 'Completado',
    badgeVariant: 'completed',
    icon: '✅',
    description: 'Archivo MP3 disponible para descarga de un solo uso',
    isTerminal: true,
    isActive: false,
  },
  FAILED: {
    label: 'Fallido',
    badgeVariant: 'failed',
    icon: '❌',
    description: 'Ocurrió un error en la descarga o conversión',
    isTerminal: true,
    isActive: false,
  },
  CANCELLED: {
    label: 'Cancelado',
    badgeVariant: 'cancelled',
    icon: '🚫',
    description: 'Trabajo cancelado por el usuario',
    isTerminal: true,
    isActive: false,
  },
};

export function isValidDownloadStatus(
  status: unknown
): status is DownloadStatus {
  return typeof status === 'string' && status in STATUS_CONFIG_MAP;
}

export function getStatusConfig(status?: string | null): StatusConfig {
  if (status && isValidDownloadStatus(status)) {
    return STATUS_CONFIG_MAP[status];
  }
  return {
    label: status || 'Desconocido',
    badgeVariant: 'pending',
    icon: '❓',
    description: 'Estado no identificado',
    isTerminal: false,
    isActive: false,
  };
}

/**
 * Retorna verdadero si el trabajo está en un estado activo en la cola o en ejecución.
 * PENDING, QUEUED, DOWNLOADING, CONVERTING.
 */
export function isJobActive(status: DownloadStatus): boolean {
  return (
    status === 'PENDING' ||
    status === 'QUEUED' ||
    status === 'DOWNLOADING' ||
    status === 'CONVERTING'
  );
}

/**
 * Retorna verdadero si el trabajo puede ser cancelado según el contrato:
 * PENDING, QUEUED, DOWNLOADING, CONVERTING, siempre que no tenga ya cancelación solicitada.
 */
export function isJobCancellable(
  status: DownloadStatus,
  cancellationRequested = false
): boolean {
  if (cancellationRequested) return false;
  return isJobActive(status);
}

/**
 * Retorna verdadero si el trabajo puede ser reintentado.
 * Según el contrato API, SOLO los trabajos en estado FAILED son reintentables.
 * Otros estados provocan HTTP 409 DOWNLOAD_NOT_RETRYABLE.
 */
export function isJobRetryable(status: DownloadStatus): boolean {
  return status === 'FAILED';
}
