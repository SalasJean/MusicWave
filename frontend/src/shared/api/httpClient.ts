import { env } from '@/shared/config/env';
import {
  ApiProblemError,
  NetworkError,
  RequestAbortedError,
  normalizeProblemDetail,
} from './problemDetails';

export interface HttpRequestOptions extends Omit<RequestInit, 'body'> {
  /** Optional Idempotency-Key header value for mutation endpoints */
  idempotencyKey?: string;
  /** Request payload (object will be serialized as JSON, FormData left untouched) */
  body?: unknown;
  /** Query parameters */
  params?: Record<string, string | number | boolean | undefined | null>;
  /** Expected response type (defaults to 'json') */
  responseType?: 'json' | 'blob' | 'text';
}

export interface HttpResponseMeta {
  status: number;
  statusText: string;
  requestId?: string;
  rateLimitLimit?: number;
  rateLimitRemaining?: number;
  retryAfterSeconds?: number;
  contentDisposition?: string;
}

export interface HttpResponse<T> {
  data: T;
  meta: HttpResponseMeta;
}

/**
 * Parses the Retry-After header as integer seconds or RFC 7231 date.
 */
function parseRetryAfter(headerValue: string | null): number | undefined {
  if (!headerValue) return undefined;
  const asInt = parseInt(headerValue.trim(), 10);
  if (!isNaN(asInt) && asInt >= 0) {
    return asInt;
  }
  const asDate = Date.parse(headerValue);
  if (!isNaN(asDate)) {
    const diffSeconds = Math.ceil((asDate - Date.now()) / 1000);
    return Math.max(0, diffSeconds);
  }
  return undefined;
}

function parseHeaderInt(headerValue: string | null): number | undefined {
  if (!headerValue) return undefined;
  const val = parseInt(headerValue.trim(), 10);
  return isNaN(val) ? undefined : val;
}

/**
 * Shared native HTTP client wrapper for all MusicWave features.
 */
export async function request<T = unknown>(
  path: string,
  options: HttpRequestOptions = {}
): Promise<HttpResponse<T>> {
  const {
    idempotencyKey,
    body,
    params,
    headers: extraHeaders,
    responseType = 'json',
    signal,
    ...fetchOptions
  } = options;

  // 1. Resolve relative path
  let resolvedPath = path;
  if (
    !resolvedPath.startsWith('http://') &&
    !resolvedPath.startsWith('https://')
  ) {
    if (!resolvedPath.startsWith('/')) {
      resolvedPath = `/${resolvedPath}`;
    }
    // Prepend base path if not already included
    if (!resolvedPath.startsWith(env.apiBasePath)) {
      resolvedPath = `${env.apiBasePath}${resolvedPath}`;
    }
  }

  // 2. Append query parameters if any
  if (params) {
    const searchParams = new URLSearchParams();
    for (const [key, val] of Object.entries(params)) {
      if (val !== undefined && val !== null) {
        searchParams.append(key, String(val));
      }
    }
    const queryString = searchParams.toString();
    if (queryString) {
      resolvedPath += (resolvedPath.includes('?') ? '&' : '?') + queryString;
    }
  }

  // 3. Assemble headers
  const headers = new Headers(extraHeaders);
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json, application/problem+json');
  }

  if (idempotencyKey && idempotencyKey.trim() !== '') {
    headers.set('Idempotency-Key', idempotencyKey.trim());
  }

  let finalBody: BodyInit | undefined;
  if (body !== undefined && body !== null) {
    if (
      body instanceof FormData ||
      body instanceof Blob ||
      body instanceof URLSearchParams ||
      typeof body === 'string'
    ) {
      finalBody = body as BodyInit;
    } else {
      headers.set('Content-Type', 'application/json');
      finalBody = JSON.stringify(body);
    }
  }

  // 4. Execute fetch with error differentiation
  let response: Response;
  try {
    response = await fetch(resolvedPath, {
      ...fetchOptions,
      headers,
      body: finalBody,
      signal,
    });
  } catch (err: unknown) {
    if (
      (err instanceof DOMException && err.name === 'AbortError') ||
      (typeof err === 'object' &&
        err !== null &&
        (err as { name?: string }).name === 'AbortError')
    ) {
      throw new RequestAbortedError();
    }
    throw new NetworkError(
      'Error de conexión de red con el servidor. Verifique si el servicio backend está activo.',
      err
    );
  }

  // 5. Extract metadata headers
  const requestId = response.headers.get('X-Request-ID') || undefined;
  const retryAfterSeconds = parseRetryAfter(
    response.headers.get('Retry-After')
  );
  const rateLimitLimit = parseHeaderInt(
    response.headers.get('X-RateLimit-Limit')
  );
  const rateLimitRemaining = parseHeaderInt(
    response.headers.get('X-RateLimit-Remaining')
  );
  const contentDisposition =
    response.headers.get('Content-Disposition') || undefined;

  const meta: HttpResponseMeta = {
    status: response.status,
    statusText: response.statusText,
    requestId,
    retryAfterSeconds,
    rateLimitLimit,
    rateLimitRemaining,
    contentDisposition,
  };

  // 6. Non-2xx response handling -> RFC 9457 Problem Details
  if (!response.ok) {
    let rawBody: unknown;
    try {
      const contentType = response.headers.get('Content-Type') || '';
      if (contentType.includes('json') || contentType.includes('problem')) {
        rawBody = await response.json();
      } else {
        rawBody = await response.text();
      }
    } catch {
      rawBody = undefined;
    }

    const problem = normalizeProblemDetail(response.status, rawBody);
    throw new ApiProblemError(problem, {
      requestId,
      retryAfterSeconds,
    });
  }

  // 7. Success handling
  if (response.status === 204) {
    return { data: undefined as T, meta };
  }

  let data: T;
  if (responseType === 'blob') {
    data = (await response.blob()) as unknown as T;
  } else if (responseType === 'text') {
    data = (await response.text()) as unknown as T;
  } else {
    data = (await response.json()) as T;
  }

  return { data, meta };
}
