import { describe, it, expect } from 'vitest';
import { validateEnv } from './env';

describe('validateEnv', () => {
  it('returns default config when env variables are unset', () => {
    const config = validateEnv({});
    expect(config.apiBasePath).toBe('/api/v1');
    expect(config.securityMode).toBe('local');
    expect(config.appName).toBe('MusicWave');
  });

  it('accepts valid custom values', () => {
    const config = validateEnv({
      PUBLIC_API_BASE_PATH: '/custom/api',
      PUBLIC_SECURITY_MODE: 'jwt',
      PUBLIC_APP_NAME: 'Custom Wave',
    });
    expect(config.apiBasePath).toBe('/custom/api');
    expect(config.securityMode).toBe('jwt');
    expect(config.appName).toBe('Custom Wave');
  });

  it('rejects absolute backend URLs to enforce proxy isolation', () => {
    expect(() =>
      validateEnv({
        PUBLIC_API_BASE_PATH: 'http://localhost:8080/api/v1',
      })
    ).toThrow(/Backend URLs must be relative/);

    expect(() =>
      validateEnv({
        PUBLIC_API_BASE_PATH: 'https://api.musicwave.io',
      })
    ).toThrow(/Backend URLs must be relative/);
  });

  it('rejects invalid security modes', () => {
    expect(() =>
      validateEnv({
        PUBLIC_SECURITY_MODE: 'invalid_mode',
      })
    ).toThrow(/Expected "local" or "jwt"/);
  });
});
