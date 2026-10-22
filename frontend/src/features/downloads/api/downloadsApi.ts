import { request, type HttpResponse } from '@/shared/api/httpClient';
import type { components } from '@/generated/api-schema';

export type SingleDownloadRequest =
  components['schemas']['SingleDownloadRequest'];
export type BulkDownloadRequest = components['schemas']['BulkDownloadRequest'];
export type CreateDownloadsResponse =
  components['schemas']['CreateDownloadsResponse'];
export type DownloadPageResponse =
  components['schemas']['DownloadPageResponse'];
export type DownloadJobResponse = components['schemas']['DownloadJobResponse'];
export type DownloadControlResponse =
  components['schemas']['DownloadControlResponse'];
export type QueueStartResponse = components['schemas']['QueueStartResponse'];
export type QueueCancelResponse = components['schemas']['QueueCancelResponse'];
export type QueueClearResponse = components['schemas']['QueueClearResponse'];
export type SystemStatusResponse =
  components['schemas']['SystemStatusResponse'];
export type ToolStatusResponse = components['schemas']['ToolStatusResponse'];
export type RejectedUrlResponse = components['schemas']['RejectedUrlResponse'];

/**
 * Creates a single download job.
 * POST /api/v1/downloads
 */
export async function createDownload(
  payload: SingleDownloadRequest,
  options?: { idempotencyKey?: string; signal?: AbortSignal }
): Promise<HttpResponse<CreateDownloadsResponse>> {
  return request<CreateDownloadsResponse>('/downloads', {
    method: 'POST',
    body: payload,
    idempotencyKey: options?.idempotencyKey,
    signal: options?.signal,
  });
}

/**
 * Creates download jobs in bulk from a list of URLs (max 100).
 * POST /api/v1/downloads/bulk
 */
export async function createBulkDownloads(
  payload: BulkDownloadRequest,
  options?: { idempotencyKey?: string; signal?: AbortSignal }
): Promise<HttpResponse<CreateDownloadsResponse>> {
  return request<CreateDownloadsResponse>('/downloads/bulk', {
    method: 'POST',
    body: payload,
    idempotencyKey: options?.idempotencyKey,
    signal: options?.signal,
  });
}

/**
 * Imports URLs from a UTF-8 text file (max 256 KiB).
 * POST /api/v1/downloads/import
 */
export async function importDownloadsText(
  file: File | Blob,
  options?: { idempotencyKey?: string; signal?: AbortSignal }
): Promise<HttpResponse<CreateDownloadsResponse>> {
  const formData = new FormData();
  formData.append('file', file);

  return request<CreateDownloadsResponse>('/downloads/import', {
    method: 'POST',
    body: formData,
    idempotencyKey: options?.idempotencyKey,
    signal: options?.signal,
  });
}

/**
 * Retrieves a paginated list of owned download jobs.
 * GET /api/v1/downloads
 */
export async function getDownloads(options?: {
  page?: number;
  size?: number;
  signal?: AbortSignal;
}): Promise<HttpResponse<DownloadPageResponse>> {
  return request<DownloadPageResponse>('/downloads', {
    method: 'GET',
    params: {
      page: options?.page ?? 0,
      size: options?.size ?? 20,
    },
    signal: options?.signal,
  });
}

/**
 * Retrieves a single owned download job by UUID.
 * GET /api/v1/downloads/{id}
 */
export async function getDownloadById(
  id: string,
  options?: { signal?: AbortSignal }
): Promise<HttpResponse<DownloadJobResponse>> {
  return request<DownloadJobResponse>(`/downloads/${encodeURIComponent(id)}`, {
    method: 'GET',
    signal: options?.signal,
  });
}

/**
 * Cancels an individual owned download job.
 * POST /api/v1/downloads/{id}/cancel
 */
export async function cancelDownload(
  id: string,
  options?: { signal?: AbortSignal }
): Promise<HttpResponse<DownloadControlResponse>> {
  return request<DownloadControlResponse>(
    `/downloads/${encodeURIComponent(id)}/cancel`,
    {
      method: 'POST',
      signal: options?.signal,
    }
  );
}

/**
 * Retries an owned download job in FAILED status.
 * POST /api/v1/downloads/{id}/retry
 */
export async function retryDownload(
  id: string,
  options?: { signal?: AbortSignal }
): Promise<HttpResponse<DownloadControlResponse>> {
  return request<DownloadControlResponse>(
    `/downloads/${encodeURIComponent(id)}/retry`,
    {
      method: 'POST',
      signal: options?.signal,
    }
  );
}

/**
 * Queues all pending jobs for the current user.
 * POST /api/v1/queue/start
 */
export async function startQueue(options?: {
  signal?: AbortSignal;
}): Promise<HttpResponse<QueueStartResponse>> {
  return request<QueueStartResponse>('/queue/start', {
    method: 'POST',
    signal: options?.signal,
  });
}

/**
 * Cancels all active (pending, queued, downloading, converting) jobs for current user.
 * POST /api/v1/queue/cancel
 */
export async function cancelQueue(options?: {
  signal?: AbortSignal;
}): Promise<HttpResponse<QueueCancelResponse>> {
  return request<QueueCancelResponse>('/queue/cancel', {
    method: 'POST',
    signal: options?.signal,
  });
}

/**
 * Hides completed jobs and marks artifacts for immediate deletion.
 * DELETE /api/v1/queue/completed
 */
export async function clearCompletedQueue(options?: {
  signal?: AbortSignal;
}): Promise<HttpResponse<QueueClearResponse>> {
  return request<QueueClearResponse>('/queue/completed', {
    method: 'DELETE',
    signal: options?.signal,
  });
}

/**
 * Cancels active work and hides all visible jobs from the queue.
 * DELETE /api/v1/queue
 */
export async function clearQueue(options?: {
  signal?: AbortSignal;
}): Promise<HttpResponse<QueueClearResponse>> {
  return request<QueueClearResponse>('/queue', {
    method: 'DELETE',
    signal: options?.signal,
  });
}

/**
 * Retrieves system status and service readiness summary.
 * GET /api/v1/system/status
 */
export async function getSystemStatus(options?: {
  signal?: AbortSignal;
}): Promise<HttpResponse<SystemStatusResponse>> {
  return request<SystemStatusResponse>('/system/status', {
    method: 'GET',
    signal: options?.signal,
  });
}

/**
 * Retrieves worker tool status (yt-dlp and ffmpeg).
 * GET /api/v1/tools/status
 */
export async function getToolStatus(options?: {
  signal?: AbortSignal;
}): Promise<HttpResponse<ToolStatusResponse>> {
  return request<ToolStatusResponse>('/tools/status', {
    method: 'GET',
    signal: options?.signal,
  });
}
