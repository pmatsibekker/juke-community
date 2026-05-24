import { defineConfig, devices } from '@playwright/test';

/**
 * Read environment variables from file.
 * https://github.com/motdotla/dotenv
 */
// require('dotenv').config();

/**
 * See https://playwright.dev/docs/test-configuration.
 */
export default defineConfig({
  testDir: './e2e',
  /* Run tests in files in parallel */
  fullyParallel: true,
  /* Fail the build on CI if you accidentally left test.only in the source code. */
  forbidOnly: !!process.env.CI,
  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,
  /* Opt out of parallel tests on CI. */
  workers: process.env.CI ? 1 : undefined,
  /* Reporter to use. See https://playwright.dev/docs/test-reporters */
  reporter: 'html',
  /* After the whole run, turn any collected window.__coverage__ data into an
     nyc report. No-op when no coverage data was gathered. */
  globalTeardown: './e2e/global-teardown.ts',
  /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
  use: {
    /* Base URL to use in actions like `await page.goto('/')`. */
    // baseURL: 'http://127.0.0.1:3000',

    /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
    trace: 'on-first-retry',
  },

  /* Configure projects for major browsers */
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },

    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },

    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },

    /* Test against mobile viewports. */
    // {
    //   name: 'Mobile Chrome',
    //   use: { ...devices['Pixel 5'] },
    // },
    // {
    //   name: 'Mobile Safari',
    //   use: { ...devices['iPhone 12'] },
    // },

    /* Test against branded browsers. */
    // {
    //   name: 'Microsoft Edge',
    //   use: { ...devices['Desktop Edge'], channel: 'msedge' },
    // },

    /**
     * Google Chrome — used by juke-cookie-replay.spec.ts.
     * The spec itself sets channel: 'chrome', headless: false, and slowMo
     * via test.use(), so those settings apply regardless of which project runs it.
     * Run only this project with:
     *   npx playwright test --project="Google Chrome" e2e/juke-cookie-replay.spec.ts
     */
    {
      name: 'Google Chrome',
      use: { ...devices['Desktop Chrome'], channel: 'chrome' },
      testMatch: '**/juke-cookie-replay.spec.ts',
    },

    /**
     * Juke Demo — visual walk-through (juke-demo.spec.ts).
     * The spec overrides headless: false and slowMo via test.use() so the
     * demo plays at human-readable speed in a real Chrome window.
     *
     * timeout is raised to 120 s per test: each phase runs ~30-35 s at
     * the default pacing (slowMo=2000, READ_MS=3000, INTRO_MS=4500).
     *
     * Run with:
     *   npx playwright test --project="Juke Demo" e2e/juke-demo.spec.ts
     */
    {
      name: 'Juke Demo',
      use: { ...devices['Desktop Chrome'], channel: 'chrome' },
      testMatch: '**/juke-demo.spec.ts',
      timeout: 120_000,
    },

    /**
     * Coverage — drives the greeting React SPA so functional UI coverage is
     * collected (greeting-ui.spec.ts). Requires the server to be running the
     * coverage build of the SPA: build the jar with `mvn package -Pcoverage`.
     *
     * Run with:
     *   npx playwright test --project="Coverage" e2e/greeting-ui.spec.ts
     */
    {
      name: 'Coverage',
      use: { ...devices['Desktop Chrome'], channel: 'chrome' },
      testMatch: '**/greeting-ui.spec.ts',
    },
  ],

  /* Run your local dev server before starting the tests */
  // webServer: {
  //   command: 'npm run start',
  //   url: 'http://127.0.0.1:3000',
  //   reuseExistingServer: !process.env.CI,
  // },
});
