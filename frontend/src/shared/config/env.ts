export interface FrontendEnv {
  apiBasePath: string;
  securityMode: 'local' | 'jwt';
  appName: string;
}

export function validateEnv(
  env: Record<string, string | undefined>
): FrontendEnv {
  const apiBasePath = env.PUBLIC_API_BASE_PATH ?? '/api/v1';

  // Rule: Do not allow absolute host URLs in frontend components or config to enforce proxy usage
  if (apiBasePath.startsWith('http://') || apiBasePath.startsWith('https://')) {
    throw new Error(
      `Invalid PUBLIC_API_BASE_PATH "${apiBasePath}": Backend URLs must be relative (e.g. "/api/v1") to use the proxy.`
    );
  }

  const rawMode = env.PUBLIC_SECURITY_MODE ?? 'local';
  if (rawMode !== 'local' && rawMode !== 'jwt') {
    throw new Error(
      `Invalid PUBLIC_SECURITY_MODE "${rawMode}": Expected "local" or "jwt".`
    );
  }

  const appName = env.PUBLIC_APP_NAME ?? 'MusicWave';

  return {
    apiBasePath,
    securityMode: rawMode,
    appName,
  };
}

export const env: FrontendEnv = validateEnv({
  PUBLIC_API_BASE_PATH: import.meta.env.PUBLIC_API_BASE_PATH,
  PUBLIC_SECURITY_MODE: import.meta.env.PUBLIC_SECURITY_MODE,
  PUBLIC_APP_NAME: import.meta.env.PUBLIC_APP_NAME,
});
