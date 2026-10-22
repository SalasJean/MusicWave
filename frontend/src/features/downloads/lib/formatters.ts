/**
 * Formats or truncates a URL to serve as a reliable fallback title
 * before asynchronous YouTube oEmbed title resolution completes.
 */
export function formatFallbackTitle(url?: string | null): string {
  if (!url || typeof url !== 'string' || url.trim() === '') {
    return 'Descarga sin título';
  }

  try {
    const parsed = new URL(url);
    const vParam = parsed.searchParams.get('v');
    if (vParam) {
      return `YouTube (${vParam})`;
    }
    const pathname = parsed.pathname.replace(/\/$/, '');
    if (pathname && pathname !== '/') {
      const segments = pathname.split('/').filter(Boolean);
      const last = segments[segments.length - 1];
      if (last && last.length <= 40) {
        return `${parsed.hostname}/${last}`;
      }
    }
    return `${parsed.hostname}${pathname}`.slice(0, 45);
  } catch {
    return url.slice(0, 45);
  }
}

/**
 * Returns either the resolved media title or the formatted URL fallback.
 */
export function resolveDisplayTitle(
  title?: string | null,
  titleStatus?: string | null,
  sourceUrl?: string | null
): { title: string; isFallback: boolean } {
  if (title && title.trim() !== '' && titleStatus !== 'PENDING') {
    return { title: title.trim(), isFallback: false };
  }
  return { title: formatFallbackTitle(sourceUrl), isFallback: true };
}

/**
 * Formats ISO-8601 UTC date string into a local readable timestamp.
 */
export function formatDate(isoDate?: string | null): string {
  if (!isoDate) return '—';
  try {
    const d = new Date(isoDate);
    if (isNaN(d.getTime())) return isoDate;
    return new Intl.DateTimeFormat('es-ES', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }).format(d);
  } catch {
    return isoDate;
  }
}

/**
 * Maps known MusicWave RFC 9457 error codes into friendly user messages.
 */
export function formatJobError(
  code?: string | null,
  message?: string | null
): string {
  if (!code && !message) return 'Error desconocido durante el procesamiento.';

  const codeMap: Record<string, string> = {
    URL_DESTINATION_NOT_ALLOWED:
      'Destino no permitido: la URL apunta a una dirección de red privada o no enrutable.',
    URL_HOST_UNRESOLVED:
      'No se pudo resolver el nombre del host. Verifique la dirección.',
    URL_INVALID: 'La dirección URL proporcionada tiene un formato no válido.',
    IDEMPOTENCY_KEY_REUSED:
      'Esta clave de idempotencia ya fue utilizada con una carga de datos diferente.',
    DOWNLOAD_QUOTA_EXCEEDED:
      'Límite de descargas activas alcanzado para su cuenta. Espere a que finalicen las actuales.',
    RATE_LIMIT_EXCEEDED:
      'Límite de solicitudes alcanzado. Espere unos momentos antes de reintentar.',
    DOWNLOAD_NOT_RETRYABLE:
      'Solo los trabajos en estado FAILED pueden ser reintentados.',
    DOWNLOAD_NOT_FOUND:
      'El trabajo solicitado no existe o no pertenece a su usuario.',
    ARTIFACT_NOT_READY: 'El archivo MP3 aún no ha terminado de convertirse.',
    ARTIFACT_ALREADY_DOWNLOADED:
      'Este archivo MP3 ya fue descargado una vez y ha sido purgado (un solo uso).',
    ARTIFACT_EXPIRED:
      'El enlace de descarga del archivo MP3 ha caducado (límite de 1 hora superado).',
    ARTIFACT_DELIVERY_IN_PROGRESS:
      'La descarga del archivo MP3 ya está siendo transferida en otra pestaña.',
    ARTIFACT_MISSING:
      'El archivo MP3 no se encontró en el almacenamiento del servidor.',
    ARTIFACT_STORAGE_UNAVAILABLE:
      'El almacenamiento temporal de archivos no está disponible momentáneamente.',
  };

  if (code && codeMap[code]) {
    return codeMap[code];
  }

  return message || code || 'Error de procesamiento en el worker.';
}
