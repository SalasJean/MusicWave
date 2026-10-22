import { request, type HttpResponseMeta } from '@/shared/api/httpClient';

export interface ArtifactDownloadResult {
  blob: Blob;
  filename: string;
  meta: HttpResponseMeta;
}

/**
 * Extracts a safe filename from the Content-Disposition header.
 */
export function extractFilenameFromHeader(
  contentDisposition?: string,
  fallbackFilename = 'download.mp3'
): string {
  if (!contentDisposition) return fallbackFilename;

  // 1. Try RFC 5987 / RFC 6266 format: filename*=UTF-8''encoded_name
  const utf8Match = /filename\*=UTF-8''([^;]+)/i.exec(contentDisposition);
  if (utf8Match && utf8Match[1]) {
    try {
      return decodeURIComponent(utf8Match[1].trim());
    } catch {
      // fallback
    }
  }

  // 2. Try standard format: filename="name" or filename=name
  const standardMatch = /filename="?([^";]+)"?/i.exec(contentDisposition);
  if (standardMatch && standardMatch[1]) {
    return standardMatch[1].trim();
  }

  return fallbackFilename;
}

/**
 * Downloads a completed one-shot MP3 artifact for an owned job.
 * Note: ADR-0006 dictates that this is a single-use operation.
 * GET /api/v1/downloads/{id}/artifact
 */
export async function getArtifactBlob(
  id: string,
  options?: { signal?: AbortSignal }
): Promise<ArtifactDownloadResult> {
  const response = await request<Blob>(
    `/downloads/${encodeURIComponent(id)}/artifact`,
    {
      method: 'GET',
      responseType: 'blob',
      headers: {
        Accept: 'audio/mpeg, application/problem+json',
      },
      signal: options?.signal,
    }
  );

  const filename = extractFilenameFromHeader(
    response.meta.contentDisposition,
    `musicwave-${id}.mp3`
  );

  return {
    blob: response.data,
    filename,
    meta: response.meta,
  };
}
