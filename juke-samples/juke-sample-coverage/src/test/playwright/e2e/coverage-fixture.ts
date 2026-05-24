import { test as base, expect, Page } from '@playwright/test'
import * as fs from 'fs'
import * as path from 'path'
import * as crypto from 'crypto'

const NYC_OUTPUT = path.join(__dirname, '..', '.nyc_output')

/**
 * Reads window.__coverage__ off the given page and writes it as a JSON file
 * into .nyc_output/.  Returns the number of files (keys) captured, or 0 when
 * the page has no Istanbul instrumentation (e.g. /coverage, /favicon.ico, or
 * the SPA built without vite-plugin-istanbul).
 *
 * Exported so a test can checkpoint coverage before navigating away from the
 * instrumented SPA — the auto-harvester runs after `use()` returns, and by
 * then the page may have already navigated to a non-instrumented URL.
 */
export async function captureCoverage(page: Page, label: string): Promise<number> {
  try {
    const coverage = await page.evaluate(() => (window as any).__coverage__)
    const keys = coverage ? Object.keys(coverage).length : 0
    if (keys === 0) return 0
    if (!fs.existsSync(NYC_OUTPUT)) {
      fs.mkdirSync(NYC_OUTPUT, { recursive: true })
    }
    const hash     = crypto.randomBytes(8).toString('hex')
    const safeName = label.replace(/[^a-zA-Z0-9]/g, '_')
    const file     = path.join(NYC_OUTPUT, `coverage_${safeName}_${hash}.json`)
    fs.writeFileSync(file, JSON.stringify(coverage))
    return keys
  } catch {
    // Page may have closed already — not fatal.
    return 0
  }
}

/**
 * Extended Playwright test fixture that automatically collects Istanbul
 * code-coverage data (window.__coverage__) from the browser after each test
 * and writes it as JSON into .nyc_output/. Global-teardown then turns the
 * accumulated data into an nyc report.
 *
 * Prerequisites:
 *   1. Server built with `mvn package -Pcoverage` (vite-plugin-istanbul on)
 *   2. Server started with demo-start-server (-javaagent + juke.coverage.*)
 *   3. `npx playwright test` (or one of the demo-run-playwright scripts)
 *
 * Tests can also call `captureCoverage(page, label)` manually at any
 * checkpoint (typically just before navigating away from the SPA to a
 * non-instrumented page like /coverage).
 */
export const test = base.extend<{ collectCoverage: void }, {}>({
  collectCoverage: [async ({ page }, use, testInfo) => {
    await use()
    // No-op when the final page has no coverage data (e.g. the test ended
    // on /coverage); duplicates are harmless because each file gets a fresh
    // random suffix and the teardown merges them.
    await captureCoverage(page, testInfo.title)
  }, { scope: 'test', auto: true }],
})

export { expect }
