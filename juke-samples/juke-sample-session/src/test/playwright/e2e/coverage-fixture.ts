import { test as base, expect } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import * as crypto from 'crypto';

const NYC_OUTPUT = path.join(__dirname, '..', '.nyc_output');

/**
 * Extended Playwright test fixture that automatically collects Istanbul
 * code-coverage data (window.__coverage__) from the browser after each test
 * and writes it as a JSON file into .nyc_output/.
 *
 * Usage:
 *   import { test, expect } from './coverage-fixture';
 *   // then write tests as normal — coverage is collected automatically.
 *
 * Prerequisites:
 *   1. Start the app with: npm run start:coverage
 *      (this sets BABEL_ENV=coverage, activating babel-plugin-istanbul)
 *   2. Run Playwright tests: npx playwright test
 *   3. Generate report: npx nyc report --reporter=html --reporter=text
 */
export const test = base.extend<{}, { collectCoverage: void }>({
  /**
   * After each test, pull window.__coverage__ from the page and persist it.
   */
  collectCoverage: [async ({ page }, use, testInfo) => {
    // Run the test
    await use();

    // After test completes, try to collect coverage
    try {
      const coverage = await page.evaluate(() => (window as any).__coverage__);
      if (coverage) {
        // Ensure output directory exists
        if (!fs.existsSync(NYC_OUTPUT)) {
          fs.mkdirSync(NYC_OUTPUT, { recursive: true });
        }

        // Write coverage with a unique filename per test
        const hash = crypto.randomBytes(8).toString('hex');
        const safeName = testInfo.title.replace(/[^a-zA-Z0-9]/g, '_');
        const filename = `coverage_${safeName}_${hash}.json`;
        fs.writeFileSync(
          path.join(NYC_OUTPUT, filename),
          JSON.stringify(coverage),
        );
      }
    } catch {
      // Page may have already closed or navigated away — not fatal
    }
  }, { scope: 'test', auto: true }],
});

export { expect };

