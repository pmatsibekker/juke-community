import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import istanbul from 'vite-plugin-istanbul'

/*
 * Vite config for the Juke coverage demo SPA.
 *
 * Coverage:
 *   `vite build --mode coverage` (npm run build:coverage) adds
 *   vite-plugin-istanbul, which instruments the bundle so the running SPA
 *   records functional UI coverage into window.__coverage__. Playwright's
 *   coverage fixture (or any manual harvester) then turns that object into
 *   an nyc report whose coverage-summary.json is what /service/coverage/ui
 *   reads.
 *
 *   forceBuildInstrument:true is required — the plugin instruments the dev
 *   server by default but NOT a production `vite build` without it.
 *
 *   A plain `vite build` (npm run build) ships a clean, uninstrumented
 *   bundle, so production deployments carry no instrumentation.
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
      // Dev-only convenience: `npm run dev` proxies API + coverage calls to
      // the Spring Boot backend. The production bundle is served by the jar
      // on the same origin, so no proxy is involved there.
      proxy: {
        '/api':     'http://localhost:8080',
        '/service': 'http://localhost:8080',
        '/coverage':'http://localhost:8080',
      },
    },
  }
})
