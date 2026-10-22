import { getViteConfig } from 'astro/config';
import { fileURLToPath } from 'node:url';

export default getViteConfig({
  test: {
    environment: 'node',
    globals: true,
    include: ['src/**/*.{test,spec}.{js,ts}', 'tests/**/*.{test,spec}.{js,ts}'],
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
} as unknown as Parameters<typeof getViteConfig>[0]);
