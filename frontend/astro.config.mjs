import { defineConfig } from 'astro/config';
import tailwindcss from '@tailwindcss/vite';

// https://astro.build/config
export default defineConfig({
  vite: {
    plugins: [tailwindcss()],
    server: {
      proxy: {
        '/api': {
          target: process.env.BACKEND_INTERNAL_URL || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  },
});
