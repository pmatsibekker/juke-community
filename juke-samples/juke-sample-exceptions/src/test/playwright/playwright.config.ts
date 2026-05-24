import { defineConfig, devices } from '@playwright/test'
import * as path from 'path'

/*
 * Playwright config for the exception-flow demo.
 *
 *   - Server is expected on http://localhost:8080 (demo-start-server)
 *   - Runs headed and slowed down so a human can WATCH each of the four runs
 *     and read every 5-second confirmation popup
 *   - global-teardown turns harvested window.__coverage__ into an nyc report at
 *     ~/juke-demo/coverage/ui/, where /service/coverage/ui reads it
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  reporter: 'list',

  // One test drives all four runs end-to-end: record (~18s) + replay (~18s) +
  // replay-with-delay (~26s) + replay-with-exception (~18s) + a >10s coverage
  // report hold, all with slowMo on top. 300s is a safe ceiling.
  timeout: 300_000,

  use: {
    baseURL: 'http://localhost:8080',
    // Headed by default so the demo is watchable; set JUKE_HEADLESS=1 to run
    // it without a visible browser (e.g. for CI or a quick verification).
    headless: !!process.env.JUKE_HEADLESS,
    trace: 'off',
    video: 'off',
    // Slow every action so the headed browser is followable. The 5-second
    // popups and explicit read-pauses in the spec are layered on top.
    launchOptions: {
      slowMo: 600,
    },
  },

  projects: [
    {
      name: 'Exception Flow Demo',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  globalTeardown: path.resolve(__dirname, './e2e/global-teardown.ts'),
})
