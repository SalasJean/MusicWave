/**
 * RFC 9457 Problem Details interface
 */
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  code: string;
  instance?: string;
  invalidParams?: Array<{
    name: string;
    reason: string;
  }>;
}

/**
 * Error thrown when the backend responds with a non-2xx status code and RFC 9457 body.
 */
export class ApiProblemError extends Error {
  readonly status: number;
  readonly code: string;
  readonly problem: ProblemDetail;
  readonly requestId?: string;
  readonly retryAfterSeconds?: number;

  constructor(
    problem: ProblemDetail,
    options?: { requestId?: string; retryAfterSeconds?: number }
  ) {
    super(problem.detail || problem.title || `HTTP Error ${problem.status}`);
    this.name = 'ApiProblemError';
    this.status = problem.status;
    this.code = problem.code;
    this.problem = problem;
    this.requestId = options?.requestId;
    this.retryAfterSeconds = options?.retryAfterSeconds;
  }
}

/**
 * Error thrown when network connectivity fails before a response is received.
 */
export class NetworkError extends Error {
  readonly originalError?: unknown;

  constructor(
    message = 'No fue posible conectar con el servidor. Verifique su conexión de red.',
    originalError?: unknown
  ) {
    super(message);
    this.name = 'NetworkError';
    this.originalError = originalError;
  }
}

/**
 * Error thrown when a request is deliberately aborted via AbortSignal.
 */
export class RequestAbortedError extends Error {
  constructor(message = 'La petición fue cancelada.') {
    super(message);
    this.name = 'RequestAbortedError';
  }
}

/**
 * Normalizes any response body or error into a standard RFC 9457 ProblemDetail object.
 */
export function normalizeProblemDetail(
  status: number,
  raw: unknown
): ProblemDetail {
  if (typeof raw === 'object' && raw !== null) {
    const obj = raw as Record<string, unknown>;
    return {
      type: typeof obj.type === 'string' ? obj.type : 'about:blank',
      title:
        typeof obj.title === 'string'
          ? obj.title
          : getDefaultTitleForStatus(status),
      status: typeof obj.status === 'number' ? obj.status : status,
      detail:
        typeof obj.detail === 'string'
          ? obj.detail
          : typeof obj.message === 'string'
            ? obj.message
            : getDefaultTitleForStatus(status),
      code:
        typeof obj.code === 'string'
          ? obj.code
          : getDefaultCodeForStatus(status),
      instance: typeof obj.instance === 'string' ? obj.instance : undefined,
      invalidParams: Array.isArray(obj.invalidParams)
        ? (obj.invalidParams as Array<{ name: string; reason: string }>)
        : undefined,
    };
  }

  return {
    type: 'about:blank',
    title: getDefaultTitleForStatus(status),
    status,
    detail:
      typeof raw === 'string' && raw.trim() !== ''
        ? raw
        : getDefaultTitleForStatus(status),
    code: getDefaultCodeForStatus(status),
  };
}

function getDefaultTitleForStatus(status: number): string {
  switch (status) {
    case 400:
      return 'Bad Request';
    case 401:
      return 'Authentication Required';
    case 403:
      return 'Access Denied';
    case 404:
      return 'Resource Not Found';
    case 409:
      return 'Conflict';
    case 410:
      return 'Gone';
    case 429:
      return 'Too Many Requests';
    case 500:
      return 'Internal Server Error';
    case 503:
      return 'Service Unavailable';
    default:
      return `HTTP ${status}`;
  }
}

function getDefaultCodeForStatus(status: number): string {
  switch (status) {
    case 400:
      return 'BAD_REQUEST';
    case 401:
      return 'AUTHENTICATION_REQUIRED';
    case 403:
      return 'ACCESS_DENIED';
    case 404:
      return 'NOT_FOUND';
    case 409:
      return 'CONFLICT';
    case 410:
      return 'GONE';
    case 429:
      return 'RATE_LIMIT_EXCEEDED';
    case 503:
      return 'SERVICE_UNAVAILABLE';
    default:
      return 'INTERNAL_ERROR';
  }
}
