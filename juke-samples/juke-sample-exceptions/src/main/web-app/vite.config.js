import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import istanbul from 'vite-plugin-istanbul'

/*
 * Vite config for the Juke exception-flow demo SPA.
 *
 * `vite build --mode coverage` (npm run build:coverage) adds
 * vite-plugin-istanbul so the running SPA records functional UI coverage into
 * window.__coverage__. Playwright's coverage fixture turns that into an nyc
 * report whose coverage-summary.json is what /service/coverage/ui reads.
 * forceBuildInstrument:true is required to instrument a production build.
 *
 * A plain `vite build` ships a clean, uninstrumented bundle.
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
      // Generate straight into the module's target/ (served from the jar's
      // classpath at /), not a source-tree dist/. This keeps the SPA a proper
      // build artifact: `mvn clean` wipes it, so stale bundles can't linger.
      outDir: '../../../target/classes/static',
      emptyOutDir: true,
    },
    server: {
      // Dev-only: `npm run dev` proxies API + control + coverage calls to the
      // Spring Boot backend. The production bundle is served by the jar on the
      // same origin, so no proxy is involved there.
      proxy: {
        '/api':      'http://localhost:8080',
        '/service':  'http://localhost:8080',
        '/coverage': 'http://localhost:8080',
      },
    },
  }
})
