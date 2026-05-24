import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import istanbul from 'vite-plugin-istanbul'

/*
 * Vite config for the Juke greeting sample SPA.
 *
 * Coverage:
 *   `vite build --mode coverage` (npm run build:coverage) adds
 *   vite-plugin-istanbul, which instruments the bundle so the running SPA
 *   records functional UI coverage into window.__coverage__. Playwright's
 *   coverage fixture then harvests that object and nyc turns it into a report.
 *   forceBuildInstrument:true is required — the plugin instruments the dev
 *   server by default but NOT a production `vite build` without it.
 *
 *   A plain `vite build` (npm run build) ships a clean, uninstrumented bundle,
 *   so production deployments carry no instrumentation.
 *
 * https://vitejs.dev/config/
 */
export default defineConfig(({ mode }) => {
  const coverage = mode === 'coverage'

  return {
    plugins: [
      react(),
      ...(coverage
        ? [istanbul({ extension: ['.js', '.jsx'], forceBuildInstrument: true })]
        : []),
    ],
    build: {
      outDir: 'dist',
      emptyOutDir: true,
    },
    server: {
      // Dev-only convenience: `npm run dev` proxies API calls to the Spring
      // Boot backend. The production bundle is served by the jar on the same
      // origin as the API, so no proxy is involved there.
      proxy: {
        '/greeting': 'http://localhost:8080',
        '/service': 'http://localhost:8080',
      },
    },
  }
})
