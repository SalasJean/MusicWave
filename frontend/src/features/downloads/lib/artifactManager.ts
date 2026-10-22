import {
  getArtifactBlob,
  type ArtifactDownloadResult,
} from '../api/artifactApi';
import { ApiProblemError, NetworkError } from '@/shared/api/problemDetails';

export type ArtifactStatus =
  'idle' | 'delivering' | 'downloaded' | 'expired' | 'missing' | 'error';

export interface ArtifactActionResult {
  status: ArtifactStatus;
  message: string;
  canRetry: boolean;
  filename?: string;
}

/**
 * Triggers native browser download by generating a temporary Blob URL and revoking it immediately.
 * CRITICAL ADR-0006 REQUIREMENT:
 * The blob is NEVER stored in localStorage, sessionStorage, IndexedDB, Cache API, or Service Workers.
 */
export function saveBlobToDisk(blob: Blob, filename: string): void {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.style.display = 'none';

  document.body.appendChild(anchor);
  anchor.click();

  // Cleanup immediately
  setTimeout(() => {
    document.body.removeChild(anchor);
    URL.revokeObjectURL(url);
  }, 100);
}

/**
 * Translates HTTP errors into ADR-0006 compliant artifact statuses and messages.
 */
export function handleArtifactError(err: unknown): ArtifactActionResult {
  if (err instanceof ApiProblemError) {
    switch (err.code) {
      case 'ARTIFACT_ALREADY_DOWNLOADED':
        return {
          status: 'downloaded',
          message:
            'El archivo MP3 ya fue descargado previamente y eliminado del servidor (un solo uso según ADR-0006).',
          canRetry: false,
        };

      case 'ARTIFACT_EXPIRED':
        return {
          status: 'expired',
          message:
            'El archivo MP3 ha expirado y ha sido eliminado del servidor (límite de 1 hora superado).',
          canRetry: false,
        };

      case 'ARTIFACT_MISSING':
        return {
          status: 'missing',
          message:
            'El archivo MP3 no se encuentra disponible en el almacenamiento del servidor.',
          canRetry: false,
        };

      case 'ARTIFACT_DELIVERY_IN_PROGRESS':
        return {
          status: 'delivering',
          message:
            'La descarga del archivo MP3 ya se encuentra en curso en otra solicitud.',
          canRetry: true,
        };

      case 'ARTIFACT_NOT_READY':
        return {
          status: 'idle',
          message:
            'El archivo aún no ha finalizado su procesamiento y almacenamiento.',
          canRetry: true,
        };

      case 'ARTIFACT_STORAGE_UNAVAILABLE':
        return {
          status: 'error',
          message:
            'El servicio de almacenamiento temporal no se encuentra disponible. Intente nuevamente en unos minutos.',
          canRetry: true,
        };

      default:
        return {
          status: 'error',
          message:
            err.problem.detail ||
            err.problem.title ||
            'Error al recuperar el archivo MP3.',
          canRetry: err.status < 410,
        };
    }
  }

  if (err instanceof NetworkError) {
    return {
      status: 'error',
      message: err.message,
      canRetry: true,
    };
  }

  return {
    status: 'error',
    message:
      err instanceof Error ? err.message : 'Error al procesar la descarga.',
    canRetry: true,
  };
}

/**
 * Performs artifact retrieval for a single completed job according to ADR-0006.
 */
export async function retrieveArtifact(
  id: string,
  options?: { signal?: AbortSignal }
): Promise<ArtifactDownloadResult> {
  return getArtifactBlob(id, options);
}
