import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  if (!env.API_PROXY_TARGET || !env.FRONTEND_PORT) {
    throw new Error('Configura API_PROXY_TARGET y FRONTEND_PORT en el archivo .env local del frontend.');
  }
  const port = Number(env.FRONTEND_PORT);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error('FRONTEND_PORT debe ser un puerto válido.');
  }
  return {
    plugins: [react()],
    server: {
      port,
      proxy: {
        '/api': {
          target: env.API_PROXY_TARGET,
          changeOrigin: true
        }
      }
    },
    build: {
      outDir: 'dist',
      sourcemap: false
    }
  };
});
