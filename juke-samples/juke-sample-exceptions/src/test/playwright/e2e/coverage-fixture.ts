import { test as base, expect, Page } from '@playwright/test'
import * as fs from 'fs'
import * as path from 'path'
import * as crypto from 'crypto'

const NYC_OUTPUT = path.join(__dirname, '..', '.nyc_output')

/**
 * Reads window.__coverage__ off the given page and writes it as a JSON file
 * into .nyc_output/. Returns the number of files (keys) captured, or 0 when the
 * page has no Istanbul instrumentation (SPA built without vite-plugin-istanbul).
 *
 * Exported so a test can checkpoint coverage at any point — important here
 * because the single test reloads the SPA between runs, and each reload resets
 * window.__coverage__, so we snapshot before each reload.
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
    return 0
  }
}

/**
 * Extended fixture that automatically harvests window.__coverage__ after each
 * test and writes it into .nyc_output/. global-teardown turns the accumulated
 * data into an nyc report.
 */
export const test = base.extend<{ collectCoverage: void }, {}>({
  collectCoverage: [async ({ page }, use, testInfo) => {
    await use()
    await captureCoverage(page, testInfo.title)
  }, { scope: 'test', auto: true }],
})

export { expect }
