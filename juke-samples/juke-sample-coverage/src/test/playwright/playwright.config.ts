import { defineConfig, devices } from '@playwright/test'
import * as path from 'path'

/*
 * Playwright config for the coverage demo.
 *
 * Defaults match the launcher scripts:
 *   - Server is expected on http://localhost:8080
 *   - Test runs headed (so you can SEE the journey drive coverage live)
 *   - global-teardown.ts turns harvested window.__coverage__ data into an
 *     nyc report at ~/juke-demo/coverage/ui/, which is exactly where the
 *     server's /service/coverage/ui endpoint reads coverage-summary.json
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  reporter: 'list',

  // Give the full journey plenty of time: two passes × (3×STEP_PAUSE +
  // READ_PAUSE + STEP_PAUSE) + inter-pass pause + final pause + slowMo
  // overhead (~800 ms × ~10 actions) ≈ 35 s.  120 s is a safe ceiling.
  timeout: 120_000,

  use: {
    baseURL: 'http://localhost:8080',
    // Headed by default so the demo is watchable; set JUKE_HEADLESS=1 to run it
    // without a visible browser (CI / the verify-samples gate).
    headless: !!process.env.JUKE_HEADLESS,
    trace: 'off',
    video: 'off',
    // Slow down every click / fill / navigation so a human watching the
    // headed browser can follow along.  Individual spec pauses are added on
    // top of this at the dramatic moments (greeting appears, dashboard
    // updates) so there is time to actually read the output.
    launchOptions: {
      slowMo: 800,
    },
  },

  projects: [
    {
      name: 'Coverage Demo',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  globalTeardown: path.resolve(__dirname, './e2e/global-teardown.ts'),
})
