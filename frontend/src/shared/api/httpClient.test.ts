import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { request } from './httpClient';
import {
  ApiProblemError,
  NetworkError,
  RequestAbortedError,
} from './problemDetails';

describe('httpClient.request', () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it('handles successful 200 JSON responses', async () => {
    const mockData = { id: 'test-123', status: 'PENDING' };
    const mockHeaders = new Headers({
      'Content-Type': 'application/json',
      'X-Request-ID': 'req-abc-123',
      'X-RateLimit-Limit': '100',
      'X-RateLimit-Remaining': '99',
    });

    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(mockData), {
        status: 200,
        statusText: 'OK',
        headers: mockHeaders,
      })
    );

    const result = await request<typeof mockData>('/downloads', {
      params: { page: 0, size: 20 },
    });

    expect(result.data).toEqual(mockData);
    expect(result.meta.status).toBe(200);
    expect(result.meta.requestId).toBe('req-abc-123');
    expect(result.meta.rateLimitLimit).toBe(100);
    expect(result.meta.rateLimitRemaining).toBe(99);

    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/downloads?page=0&size=20',
      expect.objectContaining({
        headers: expect.any(Headers),
      })
    );
  });

  it('sends Idempotency-Key header when provided', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ replayed: false }), {
        status: 201,
        headers: new Headers({ 'Content-Type': 'application/json' }),
      })
    );

    await request('/downloads', {
      method: 'POST',
      body: { url: 'https://example.com/audio' },
      idempotencyKey: 'idemp-key-xyz',
    });

    const calledHeaders = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls[0][1].headers as Headers;
    expect(calledHeaders.get('Idempotency-Key')).toBe('idemp-key-xyz');
    expect(calledHeaders.get('Content-Type')).toBe('application/json');
  });

  it('handles 204 No Content responses without attempting to parse JSON', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(null, {
        status: 204,
        statusText: 'No Content',
      })
    );

    const result = await request('/queue/completed', {
      method: 'DELETE',
    });

    expect(result.data).toBeUndefined();
    expect(result.meta.status).toBe(204);
  });

  it('handles RFC 9457 Problem Details for 400 Bad Request', async () => {
    const problemPayload = {
      type: 'about:blank',
      title: 'Bad Request',
      status: 400,
      detail: 'The provided URL points to an unroutable loopback address.',
      code: 'URL_DESTINATION_NOT_ALLOWED',
    };

    globalThis.fetch = vi.fn().mockImplementation(() =>
      Promise.resolve(
        new Response(JSON.stringify(problemPayload), {
          status: 400,
          headers: new Headers({
            'Content-Type': 'application/problem+json',
            'X-Request-ID': 'req-bad-url',
          }),
        })
      )
    );

    await expect(request('/downloads')).rejects.toThrow(ApiProblemError);

    try {
      await request('/downloads');
    } catch (err: unknown) {
      const apiErr = err as ApiProblemError;
      expect(apiErr.status).toBe(400);
      expect(apiErr.code).toBe('URL_DESTINATION_NOT_ALLOWED');
      expect(apiErr.problem.detail).toBe(
        'The provided URL points to an unroutable loopback address.'
      );
      expect(apiErr.requestId).toBe('req-bad-url');
    }
  });

  it('handles 401 Authentication Required', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          status: 401,
          code: 'AUTHENTICATION_REQUIRED',
          detail: 'Bearer token missing or invalid',
        }),
        {
          status: 401,
          headers: new Headers({ 'Content-Type': 'application/problem+json' }),
        }
      )
    );

    await expect(request('/downloads')).rejects.toMatchObject({
      status: 401,
      code: 'AUTHENTICATION_REQUIRED',
    });
  });

  it('handles 403 Access Denied', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          status: 403,
          code: 'ACCESS_DENIED',
          detail: 'Insufficient permissions',
        }),
        {
          status: 403,
          headers: new Headers({ 'Content-Type': 'application/problem+json' }),
        }
      )
    );

    await expect(request('/downloads')).rejects.toMatchObject({
      status: 403,
      code: 'ACCESS_DENIED',
    });
  });

  it('handles 404 Download Not Found', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          status: 404,
          code: 'DOWNLOAD_NOT_FOUND',
          detail:
            'No download found with the specified id for the current owner',
        }),
        {
          status: 404,
          headers: new Headers({ 'Content-Type': 'application/problem+json' }),
        }
      )
    );

    await expect(request('/downloads/nonexistent')).rejects.toMatchObject({
      status: 404,
      code: 'DOWNLOAD_NOT_FOUND',
    });
  });

  it('handles 409 Conflict (e.g. DOWNLOAD_NOT_RETRYABLE or IDEMPOTENCY_KEY_REUSED)', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          status: 409,
          code: 'DOWNLOAD_NOT_RETRYABLE',
          detail: 'Job must be in FAILED status to retry',
        }),
        {
          status: 409,
          headers: new Headers({ 'Content-Type': 'application/problem+json' }),
        }
      )
    );

    await expect(request('/downloads/123/retry')).rejects.toMatchObject({
      status: 409,
      code: 'DOWNLOAD_NOT_RETRYABLE',
    });
  });

  it('handles 410 Gone for one-shot consumed or expired artifacts', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          status: 410,
          code: 'ARTIFACT_ALREADY_DOWNLOADED',
          detail:
            'The requested artifact has already been downloaded once and removed.',
        }),
        {
          status: 410,
          headers: new Headers({ 'Content-Type': 'application/problem+json' }),
        }
      )
    );

    await expect(request('/downloads/123/artifact')).rejects.toMatchObject({
      status: 410,
      code: 'ARTIFACT_ALREADY_DOWNLOADED',
    });
  });

  it('handles 429 Too Many Requests and parses numeric Retry-After', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          status: 429,
          code: 'RATE_LIMIT_EXCEEDED',
          detail: 'Rate limit quota exhausted. Please retry later.',
        }),
        {
          status: 429,
          headers: new Headers({
            'Content-Type': 'application/problem+json',
            'Retry-After': '30',
          }),
        }
      )
    );

    try {
      await request('/downloads');
      expect.fail('Should have thrown ApiProblemError');
    } catch (err: unknown) {
      const apiErr = err as ApiProblemError;
      expect(apiErr.status).toBe(429);
      expect(apiErr.code).toBe('RATE_LIMIT_EXCEEDED');
      expect(apiErr.retryAfterSeconds).toBe(30);
    }
  });

  it('handles 503 Service Unavailable', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          status: 503,
          code: 'SERVICE_UNAVAILABLE',
          detail: 'Rate limit store is temporarily unavailable',
        }),
        {
          status: 503,
          headers: new Headers({ 'Content-Type': 'application/problem+json' }),
        }
      )
    );

    await expect(request('/downloads')).rejects.toMatchObject({
      status: 503,
      code: 'SERVICE_UNAVAILABLE',
    });
  });

  it('parses HTTP-date in Retry-After header', async () => {
    const futureDate = new Date(Date.now() + 45000).toUTCString();

    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          status: 429,
          code: 'RATE_LIMIT_EXCEEDED',
        }),
        {
          status: 429,
          headers: new Headers({
            'Content-Type': 'application/problem+json',
            'Retry-After': futureDate,
          }),
        }
      )
    );

    try {
      await request('/downloads');
      expect.fail('Should have thrown');
    } catch (err: unknown) {
      const apiErr = err as ApiProblemError;
      expect(apiErr.retryAfterSeconds).toBeGreaterThanOrEqual(40);
      expect(apiErr.retryAfterSeconds).toBeLessThanOrEqual(46);
    }
  });

  it('handles cancellation via AbortSignal', async () => {
    const controller = new AbortController();
    controller.abort();

    const abortError = new DOMException(
      'The operation was aborted.',
      'AbortError'
    );
    globalThis.fetch = vi.fn().mockRejectedValue(abortError);

    await expect(
      request('/downloads', { signal: controller.signal })
    ).rejects.toThrow(RequestAbortedError);
  });

  it('differentiates network errors when fetch fails', async () => {
    globalThis.fetch = vi
      .fn()
      .mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(request('/downloads')).rejects.toThrow(NetworkError);

    try {
      await request('/downloads');
    } catch (err: unknown) {
      expect(err).toBeInstanceOf(NetworkError);
      expect((err as NetworkError).message).toContain('Error de conexión');
    }
  });
});
