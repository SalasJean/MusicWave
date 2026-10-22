/**
 * Generates a standard UUID v4 string for Idempotency-Key headers
 * using the platform's native Crypto API.
 */
export function generateIdempotencyKey(prefix = 'mw'): string {
  if (
    typeof crypto !== 'undefined' &&
    typeof crypto.randomUUID === 'function'
  ) {
    return `${prefix}-${crypto.randomUUID()}`;
  }
  // Fallback for older environments
  const s4 = () =>
    Math.floor((1 + Math.random()) * 0x10000)
      .toString(16)
      .substring(1);
  return `${prefix}-${s4()}${s4()}-${s4()}-${s4()}-${s4()}-${s4()}${s4()}${s4()}`;
}
