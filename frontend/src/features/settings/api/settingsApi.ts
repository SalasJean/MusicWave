import { request, type HttpResponse } from '@/shared/api/httpClient';
import type { components } from '@/generated/api-schema';

export type SettingsResponse = components['schemas']['SettingsResponse'];
export type UpdateSettingsRequest =
  components['schemas']['UpdateSettingsRequest'];

/**
 * Retrieves the current user's preferences (concurrency and audio quality).
 * GET /api/v1/settings
 */
export async function getSettings(options?: {
  signal?: AbortSignal;
}): Promise<HttpResponse<SettingsResponse>> {
  return request<SettingsResponse>('/settings', {
    method: 'GET',
    signal: options?.signal,
  });
}

/**
 * Updates the current user's preferences.
 * PUT /api/v1/settings
 */
export async function updateSettings(
  payload: UpdateSettingsRequest,
  options?: { signal?: AbortSignal }
): Promise<HttpResponse<SettingsResponse>> {
  return request<SettingsResponse>('/settings', {
    method: 'PUT',
    body: payload,
    signal: options?.signal,
  });
}
