import { test, expect, captureCoverage } from './coverage-fixture'

/**
 * Drives the full coverage demo journey end-to-end:
 *
 *  Recording phase (this run)
 *  1. Start a Juke recording (beforeAll)
 *  2. Open the SPA and drive three journeys (formal → royal → casual) so
 *     all three switch branches in GreetingService get exercised
 *  3. Confirm the right-hand dashboard panel shows non-zero server coverage
 *  4. Navigate to /coverage — the combined drill-down page — and verify it
 *     populated with live numbers and a JaCoCo iframe.  This is the same
 *     view a developer or CI dashboard would open to see exactly which
 *     lines / branches were missed.
 *  5. End the Juke recording — server saves coverage-demo.zip (afterAll)
 *
 *  After this run you can replay:
 *    GET /service/replay/start?track=coverage-demo
 *    ... drive the journey again (or run this spec a second time with
 *        JUKE_REPLAY_ONLY=true so it doesn't override replay mode) ...
 *    GET /service/recording/report?track=coverage-demo
 *    → report JSON includes "coverage" at the top with JaCoCo + nyc figures
 *      plus a reportUrl pointing at the same /coverage drill-down page.
 *
 *  UI coverage (window.__coverage__) is harvested automatically by the
 *  coverage-fixture after each test and written to .nyc_output/.
 *  global-teardown then generates coverage-summary.json + index.html in
 *  ~/juke-demo/coverage/ui/ so the server picks them up immediately.
 */
test.describe.configure({ mode: 'serial' })

const STEP_PAUSE  = 1200  // pause after each visible state change (ms)
const READ_PAUSE  = 2500  // pause when there is something to read (ms)
const FINAL_PAUSE = 4000  // pause at the end so the dashboard is visible (ms)

// ── Juke recording lifecycle ───────────────────────────────────────────────
// Set JUKE_REPLAY_ONLY=true to skip record/start and record/end so the
// server stays in the replay mode you started with
//   GET /service/replay/start?track=coverage-demo
// then drive this spec without overriding the replay session.
const REPLAY_ONLY = process.env['JUKE_REPLAY_ONLY'] === 'true'

test.beforeAll(async ({ request }) => {
  if (REPLAY_ONLY) {
    console.log('\nℹ️  JUKE_REPLAY_ONLY=true — skipping record/start; server stays in replay mode')
    return
  }
  // Start recording so every @Juke seam call during the journey is captured.
  // Tolerates a non-200 gracefully so the spec still runs if Juke isn't in
  // a recordable state (e.g. already recording, or juke.enabled=false).
  try {
    const resp = await request.get('/service/record/start', {
      params: { track: 'coverage-demo', label: 'Coverage Demo Journey' },
    })
    const body = await resp.text()
    if (resp.ok()) {
      console.log('\n🎙️  Juke recording started  (track: coverage-demo)')
    } else {
      console.warn('\n⚠️  Could not start Juke recording:', body)
      console.warn('    (continuing — the journey will still run, just without recording)')
    }
  } catch (e) {
    console.warn('\n⚠️  Juke record/start request failed:', (e as Error).message)
  }
})

test.afterAll(async ({ request }) => {
  if (REPLAY_ONLY) {
    console.log('\nℹ️  JUKE_REPLAY_ONLY=true — skipping record/end; replay session remains active')
  } else {
    // End the recording — the server flushes juke-metadata.json and saves the
    // ZIP to ~/juke-demo/sample-coverage/coverage-demo.zip.  We discard the
    // download (the ZIP is already persisted on the server's filesystem).
    try {
      const resp = await request.get('/service/record/end')
      if (resp.ok()) {
        console.log('\n💾 Juke recording saved → ~/juke-demo/sample-coverage/coverage-demo.zip')
      } else {
        console.warn('\n⚠️  record/end responded', resp.status(), '— ZIP may not have been saved')
      }
    } catch (e) {
      console.warn('\n⚠️  Juke record/end request failed:', (e as Error).message)
    }
  }

  console.log('\n──────────────────────────────────────────────────────────────')
  console.log('  Combined coverage drill-down (recommended):')
  console.log('    http://localhost:8080/coverage')
  console.log('    → header strip with live numbers, JaCoCo + Istanbul HTML')
  console.log('      reports side-by-side in iframes, click to drill in.')
  console.log('')
  console.log('  Or the JSON report (good for CI):')
  console.log('    http://localhost:8080/service/recording/report?track=coverage-demo')
  console.log('    → "coverage" section at the top has the same figures.')
  console.log('')
  console.log('  To also see replay sessions with call-by-call comparison:')
  console.log('    1. Restart the server (demo-start-server.bat)')
  console.log('    2. Start replay:  GET /service/replay/start?track=coverage-demo')
  console.log('    3. Drive the journey with JUKE_REPLAY_ONLY=true:')
  console.log('         set JUKE_REPLAY_ONLY=true && npx playwright test')
  console.log('    4. Call the report (same URL as above).')
  console.log('──────────────────────────────────────────────────────────────')
})

// ── Journey test ────────────────────────────────────────────────────────────

test('user runs the demo journey and the dashboard reflects it', async ({ page }) => {
  await page.goto('/')
  // Let the viewer take in the initial layout: journey on the left,
  // coverage panel on the right (shows "Loading…" until the first poll).
  await page.waitForTimeout(READ_PAUSE)

  // ── Pass 1: pick Formal ───────────────────────────────────────────────
  await runJourney(page, 'Ada Lovelace', 'formal')

  // Pause between passes so the viewer can see the server bars have
  // climbed before the second journey begins.
  await page.waitForTimeout(READ_PAUSE)

  // ── Pass 2: pick Royal (the less-traveled server branch) ──────────────
  // Exercising royal + formal covers 2 of the 3 switch branches in
  // GreetingService and brings branch coverage close to the threshold.
  await runJourney(page, 'Grace Hopper', 'royal')

  await page.waitForTimeout(READ_PAUSE)

  // ── Pass 3: pick Casual (covers the switch default branch) ────────────
  // The GreetingService switch has three cases (formal / royal / default).
  // "Casual" falls into the default arm, which was the last uncovered branch
  // keeping server branch coverage below the 50% threshold.  After this
  // pass the branch figure crosses the threshold and the dashboard turns green.
  await runJourney(page, 'Alan Turing', 'casual')

  // ── Confirm the dashboard reports server coverage > 0% ────────────────
  // The dashboard polls every 2 s; wait one full cycle after our last
  // API call, then read the label off the server line bar.
  await page.waitForTimeout(2500)

  const serverLine = await page.locator('[data-test=bar-line] .bar-fill').textContent()
  expect(parseFloat(serverLine || '0')).toBeGreaterThan(0)

  // Hold the dashboard state on screen so the viewer can read both panels
  // before we navigate away to the combined report.
  await page.waitForTimeout(READ_PAUSE)

  // Checkpoint the UI coverage NOW, while we're still on the instrumented
  // SPA. The auto-harvester in the coverage fixture runs after the test
  // body returns, and by then we'll be on /coverage which has no
  // instrumentation (so window.__coverage__ would be undefined).
  const captured = await captureCoverage(page, 'full-journey-snapshot')
  if (captured > 0) {
    console.log(`\n💾 Captured UI coverage from ${captured} source file(s) before navigating.`)
  }

  // ── Final reveal: combined coverage drill-down at /coverage ───────────
  // The /coverage page hosts both the server (JaCoCo) and UI (nyc/Istanbul)
  // HTML reports side-by-side in iframes, with a header strip showing the
  // live numbers fetched from /service/coverage.  This is what a developer
  // would open after a CI run to see exactly which lines / branches were
  // missed, drill into individual classes/files, and decide whether to add
  // more tests.
  await page.goto('/coverage')

  // Wait for the JSON fetch to populate the header strip — the badge starts
  // as "Loading…" and becomes Pass / Fail / N/A once /service/coverage
  // resolves.  The server-line metric goes from "—" to a percentage when
  // the server half is available.
  await expect(page.locator('#overall-badge')).not.toHaveText('Loading…', { timeout: 10_000 })
  await expect(page.locator('#server-line')).not.toHaveText('—', { timeout: 10_000 })

  // Log the headline numbers so they appear in the Playwright output even
  // when the spec is run head-less in CI.
  const badge      = (await page.locator('#overall-badge').textContent())?.trim()
  const sLine      = (await page.locator('#server-line').textContent())?.trim()
  const sBranch    = (await page.locator('#server-branch').textContent())?.trim()
  const uLines     = (await page.locator('#ui-lines').textContent())?.trim()
  const uBranches  = (await page.locator('#ui-branches').textContent())?.trim()
  console.log(`\n📊 Combined coverage report ( /coverage ):`)
  console.log(`     Overall:        ${badge}`)
  console.log(`     Server line:    ${sLine}     Server branch: ${sBranch}`)
  console.log(`     UI lines:       ${uLines}     UI branches:   ${uBranches}`)
  console.log(`     (UI numbers reflect the previous Playwright run; the run`)
  console.log(`      that's just finishing will update them on the next refresh.)`)

  // Confirm the server iframe loaded its JaCoCo report — the package name
  // "juke-sample-coverage" appears in the JaCoCo bundle header.  This is
  // independent of any test data, so the assertion is stable.
  const serverFrame = page.frameLocator('#server-frame')
  await expect(serverFrame.locator('text=juke-sample-coverage').first()).toBeVisible({ timeout: 10_000 })

  // Hold the combined view on screen.  Longer than other pauses because
  // the viewer needs time to scan both iframes and the metric strip.
  await page.waitForTimeout(FINAL_PAUSE)
})

// ── Shared journey helper ───────────────────────────────────────────────────

async function runJourney(page, name: string, style: string) {
  // Step 1: name
  await page.locator('[data-test=name-input]').fill(name)
  await page.waitForTimeout(STEP_PAUSE)   // viewer reads the filled name
  await page.locator('[data-test=next-to-style]').click()
  await page.waitForTimeout(STEP_PAUSE)   // viewer sees style-picker appear

  // Step 2: style
  await page.locator(`[data-test=style-${style}]`).click()
  await page.waitForTimeout(STEP_PAUSE)   // viewer sees style highlighted

  // Step 3: confirm & generate
  await page.locator('[data-test=submit]').click()

  // Wait for the greeting, then hold it on screen so it can be read
  await expect(page.locator('[data-test=greeting-text]')).toContainText(name)
  await page.waitForTimeout(READ_PAUSE)   // viewer reads the greeting + dashboard update

  // Reset for the next pass
  await page.locator('[data-test=reset]').click()
  await page.waitForTimeout(STEP_PAUSE)   // viewer sees the form reset
}
