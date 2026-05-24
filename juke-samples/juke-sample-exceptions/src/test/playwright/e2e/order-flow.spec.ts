import { test, expect, captureCoverage } from './coverage-fixture'
import type { Page } from '@playwright/test'

/**
 * Drives the SKU order journey through FOUR runs on a single headed browser so
 * a viewer can watch each one. Every confirmation popup is held on screen for
 * 5 seconds by the app itself; extra read-pauses are layered on between runs.
 *
 *   Run 1 — RECORD the happy path.
 *           Global record mode, no session cookie. The three OMS calls are
 *           captured into order-demo.zip; three COMPLETED popups appear.
 *
 *   Run 2 — REPLAY (deterministic).
 *           A per-session replay. The same three orders complete; the OMS order
 *           ids match the recording. The session report shows COMPLETED even
 *           though every confirmation number is freshly generated — proof that
 *           @JukeIgnorable is honored on the confirmation field.
 *
 *   Run 3 — REPLAY + 10s DELAY injected on the SECOND order.
 *           The client times out after 4s and shows "Your order is queued";
 *           orders 1 and 3 still complete.
 *
 *   Run 4 — REPLAY + EXCEPTION injected on the SECOND order.
 *           The OMS call throws; the server records the order for later and the
 *           UI shows "technical difficulties"; orders 1 and 3 still complete.
 *
 *   Finally — open the (separate) coverage popup and hold the drill-down report
 *   on screen for >10 seconds.
 *
 * UI coverage (window.__coverage__) is harvested by the coverage fixture and
 * turned into an nyc report by global-teardown.
 */
test.describe.configure({ mode: 'serial' })

const TRACK = 'order-demo'
const LABEL = 'SKU Order — Exception Flows'

const READ_PAUSE = 2500       // pause between runs so the viewer can absorb
const REPORT_HOLD_MS = 12_000 // hold the coverage report on screen (> 10s)

test('four runs: record, replay, replay+delay, replay+exception', async ({ page }) => {
  // ── Prepare: clean slate ────────────────────────────────────────────────
  await page.context().clearCookies()
  await page.request.get('/service/remix/clear')

  await page.goto('/')
  await expect(page.locator('[data-test=buy]')).toBeEnabled({ timeout: 15_000 })
  await page.waitForTimeout(READ_PAUSE)

  // ── Run 1: RECORD the happy path ──────────────────────────────────────────
  console.log('\n🎙️  Run 1 — RECORD (global record mode, no session cookie)')
  await expectOk(page.request.get('/service/record/start', { params: { track: TRACK, label: LABEL } }))
  await placeThreeOrders(page)
  await expectAllCompleted(page)
  await expectOk(page.request.get('/service/record/end'))
  console.log('💾 Recording saved → order-demo.zip')
  await page.waitForTimeout(READ_PAUSE)

  // Replay runs use cookie-scoped sessions (session/start), which validate the
  // track themselves and override the global mode — so no global replay/start
  // is needed. Derive the exact remix target from the recording.
  const secondOrder = await secondOrderEntry(page)
  console.log(`🎯 Remix target for the second order: ${secondOrder}`)

  // ── Run 2: REPLAY (deterministic) ─────────────────────────────────────────
  console.log('\n▶️  Run 2 — REPLAY (deterministic repeat)')
  await page.request.get('/service/remix/clear')
  await startSession(page, 'Run 2 — deterministic replay')
  await placeThreeOrders(page)
  await expectAllCompleted(page)
  await stopSession(page)

  // The session report proves @JukeIgnorable: COMPLETED despite the changing
  // confirmation numbers.
  await assertReportCompleted(page, 'Run 2 — deterministic replay')
  await page.waitForTimeout(READ_PAUSE)

  // ── Run 3: REPLAY + 10s delay on the second order → "queued" ─────────────
  console.log('\n⏳ Run 3 — REPLAY + 10s delay injected on the second order')
  await page.request.get('/service/remix/clear')
  await expectOk(page.request.get('/service/remix/delaySchedule',
      { params: { classAndMethodSequence: secondOrder, waitTimeInMS: 10_000 } }))
  await startSession(page, 'Run 3 — delay on second order')
  await page.locator('[data-test=buy]').click()
  // The second order's confirmation popup must say QUEUED.
  await expect(page.locator('[data-test=popup-status]'))
      .toHaveAttribute('data-status', 'QUEUED', { timeout: 25_000 })
  console.log('   ✔ "Your order is queued" popup shown for the second order')
  await expect(page.locator('[data-test=log-status-3]')).toBeVisible({ timeout: 60_000 })
  expect(await statusOf(page, 1)).toBe('COMPLETED')
  expect(await statusOf(page, 2)).toBe('QUEUED')
  expect(await statusOf(page, 3)).toBe('COMPLETED')
  await stopSession(page)
  await page.waitForTimeout(READ_PAUSE)

  // ── Run 4: REPLAY + exception on the second order → "technical difficulties"
  console.log('\n💥 Run 4 — REPLAY + exception injected on the second order')
  await page.request.get('/service/remix/clear')
  await expectOk(page.request.get('/service/remix/exceptionSchedule',
      { params: { classAndMethodSequence: secondOrder, exception: 'IOException',
                  exceptionMessage: 'Injected OMS outage' } }))
  await startSession(page, 'Run 4 — exception on second order')
  await page.locator('[data-test=buy]').click()
  await expect(page.locator('[data-test=popup-status]'))
      .toHaveAttribute('data-status', 'RECORDED', { timeout: 25_000 })
  await expect(page.locator('[data-test=popup-message]')).toContainText('technical difficulties')
  console.log('   ✔ "Technical difficulties" popup shown for the second order')
  await expect(page.locator('[data-test=log-status-3]')).toBeVisible({ timeout: 60_000 })
  expect(await statusOf(page, 1)).toBe('COMPLETED')
  expect(await statusOf(page, 2)).toBe('RECORDED')
  expect(await statusOf(page, 3)).toBe('COMPLETED')
  await stopSession(page)
  await page.waitForTimeout(READ_PAUSE)

  // ── Snapshot UI coverage before touching the coverage popup ───────────────
  const captured = await captureCoverage(page, 'order-journey')
  if (captured > 0) console.log(`\n💾 Captured UI coverage from ${captured} source file(s).`)

  // Force the server (JaCoCo) HTML report to be written so the report tab's
  // iframe has something to load.
  await page.request.get('/service/coverage/server')

  // ── Coverage popup: summary, then hold the report on screen > 10s ─────────
  console.log('\n📊 Opening the coverage popup (separate from the ordering screen)')
  await page.locator('[data-test=view-coverage]').click()
  await expect(page.locator('[data-test=coverage-modal]')).toBeVisible()
  await expect(page.locator('[data-test=passed-badge]')).toBeVisible({ timeout: 10_000 })

  const serverLine = await page.locator('[data-test=bar-line] .bar-fill').textContent()
  console.log(`   Server line coverage: ${serverLine?.trim()}`)
  expect(parseFloat(serverLine || '0')).toBeGreaterThan(0)
  await page.waitForTimeout(READ_PAUSE)

  // Open the drill-down report and keep it visible for > 10 seconds.
  await page.locator('[data-test=open-coverage-report]').click()
  const frame = page.frameLocator('[data-test=coverage-report-frame]')
  await expect(frame.locator('text=juke-sample-exceptions').first()).toBeVisible({ timeout: 15_000 })
  console.log(`   Holding the coverage report on screen for ${REPORT_HOLD_MS / 1000}s…`)
  await page.waitForTimeout(REPORT_HOLD_MS)

  await page.locator('[data-test=close-coverage]').click()
  console.log('\n✅ All four runs complete.')
})

// ── Helpers ──────────────────────────────────────────────────────────────────

/** Clicks Buy and waits for all three orders to land in the on-screen log. */
async function placeThreeOrders(page: Page) {
  await page.locator('[data-test=buy]').click()
  await expect(page.locator('[data-test=log-status-3]')).toBeVisible({ timeout: 60_000 })
}

async function expectAllCompleted(page: Page) {
  for (let i = 1; i <= 3; i++) {
    expect(await statusOf(page, i), `order ${i} status`).toBe('COMPLETED')
  }
}

async function statusOf(page: Page, i: number): Promise<string> {
  return (await page.locator(`[data-test=log-status-${i}]`).textContent())?.trim() || ''
}

async function startSession(page: Page, description: string) {
  const resp = await page.request.get('/service/session/start', { params: { track: TRACK, description } })
  expect(resp.ok(), `session/start for "${description}"`).toBeTruthy()
}

async function stopSession(page: Page) {
  await page.request.get('/service/session/stop')
}

async function expectOk(p: Promise<{ ok(): boolean; status(): number }>) {
  const resp = await p
  expect(resp.ok(), `control endpoint returned ${resp.status()}`).toBeTruthy()
}

/** Derives the exact remix identifier for the second OMS call from the recording. */
async function secondOrderEntry(page: Page): Promise<string> {
  const resp = await page.request.get('/service/recording/inputs', { params: { track: TRACK } })
  expect(resp.ok(), 'recording/inputs').toBeTruthy()
  const rows: Array<{ file: string; sequence: number; method: string }> = await resp.json()
  const row = rows.find(r => r.method === 'submitOrder' && r.sequence === 2)
  if (!row) throw new Error('submitOrder.2 not found in recording: ' + JSON.stringify(rows))
  return row.file.replace(/\.args\.json$/, '')
}

/** Asserts the named replay session reported COMPLETED (no input deviations). */
async function assertReportCompleted(page: Page, description: string) {
  const resp = await page.request.get('/service/recording/report', { params: { track: TRACK } })
  expect(resp.ok(), 'recording/report').toBeTruthy()
  const report = await resp.json()
  const session = (report.sessions || []).find((s: any) => s.description === description)
  expect(session, `report session "${description}"`).toBeTruthy()
  expect(session.overallStatus, 'overallStatus').toBe('COMPLETED')

  const call = (session.calls || [])[0]
  if (call) {
    console.log('   ✔ Session report COMPLETED despite changing confirmation numbers:')
    console.log(`       recorded args: ${JSON.stringify(call.recordedArguments)}`)
    console.log(`       actual args:   ${JSON.stringify(call.actualArguments)}`)
    console.log(`       inputMatched:  ${call.inputMatched}  (confirmationNumber is @JukeIgnorable)`)
  }
}
