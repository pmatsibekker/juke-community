import * as fs from 'fs'
import * as os from 'os'
import * as path from 'path'
// istanbul-lib-coverage / istanbul-lib-report / istanbul-reports ship as CJS
// modules without TS types in the playwright/nyc tree.  Use `require` so we
// don't need a separate @types dependency just for the teardown.
// eslint-disable-next-line @typescript-eslint/no-var-requires
const libCoverage  = require('istanbul-lib-coverage')
// eslint-disable-next-line @typescript-eslint/no-var-requires
const libReport    = require('istanbul-lib-report')
// eslint-disable-next-line @typescript-eslint/no-var-requires
const reports      = require('istanbul-reports')

/**
 * After every spec finishes, turn the harvested window.__coverage__ data
 * into:
 *   - coverage-summary.json — read by /service/coverage/ui for percentages
 *   - index.html (+ asset files) — the drill-down report shown in the
 *     iframe at GET /coverage and via the reportUrl field of /service/coverage
 *
 * We use istanbul-lib-coverage / istanbul-lib-report directly instead of
 * shelling out to `nyc report` because nyc 17 mis-resolves Windows absolute
 * paths and ends up reporting 0 % even when the underlying coverage data is
 * correct.  Driving the libraries ourselves keeps the same logic that nyc
 * uses internally but bypasses the path-filter bug.
 *
 * Output dir: ~/juke-demo/coverage/ui — the same location UiCoverageService
 * reads from and that the resource handler maps to /coverage/ui/**.
 *
 * No-op when no coverage data was collected (the SPA wasn't built with
 * `mvn package -Pcoverage`, so window.__coverage__ is undefined).
 */
async function globalTeardown() {
  const playwrightDir = path.resolve(__dirname, '..')
  const nycOutput     = path.join(playwrightDir, '.nyc_output')

  if (!fs.existsSync(nycOutput) || fs.readdirSync(nycOutput).filter(f => f.endsWith('.json')).length === 0) {
    console.log('\nℹ️  No UI coverage data collected (window.__coverage__ was empty).')
    console.log('   Was the SPA built with `mvn package -Pcoverage`?')
    return
  }

  const reportDir = path.join(os.homedir(), 'juke-demo', 'coverage', 'ui')
  fs.mkdirSync(reportDir, { recursive: true })

  const files = fs.readdirSync(nycOutput).filter(f => f.endsWith('.json'))
  console.log(`\n📊 Generating UI coverage report from ${files.length} coverage file(s) → ${reportDir}`)

  // ── Merge every harvested coverage map into one ───────────────────────
  // libCoverage.createCoverageMap({}) starts empty; .merge(...) accepts either
  // a raw map object (what Playwright wrote out) or another CoverageMap.
  const map = libCoverage.createCoverageMap({})
  for (const fileName of files) {
    try {
      const raw = JSON.parse(fs.readFileSync(path.join(nycOutput, fileName), 'utf-8'))
      map.merge(raw)
    } catch (e) {
      console.warn(`  ⚠️  Could not parse ${fileName} — skipping (${(e as Error).message})`)
    }
  }

  // ── Build a report context and render both html + json-summary ────────
  // Passing the CoverageMap explicitly avoids the path-filter logic that
  // nyc applies on top, which is what was returning 0 % under Windows.
  const ctx = libReport.createContext({
    dir: reportDir,
    defaultSummarizer: 'nested',
    coverageMap: map,
  })

  // HTML drill-down (index.html + per-file pages + CSS / JS assets)
  reports.create('html', { subdir: '' }).execute(ctx)
  // Machine-readable summary that the server reads via UiCoverageService
  reports.create('json-summary').execute(ctx)
  // Plain-text totals so the teardown log shows the numbers immediately
  reports.create('text-summary').execute(ctx)

  const summaryPath = path.join(reportDir, 'coverage-summary.json')
  if (fs.existsSync(summaryPath)) {
    const summary = JSON.parse(fs.readFileSync(summaryPath, 'utf-8'))
    const t = summary.total || {}
    const pct = (s: any) => (s && s.pct !== undefined) ? `${s.pct}%  (${s.covered}/${s.total})` : '—'
    console.log('\n✅ UI coverage report generated:')
    console.log(`   Lines:      ${pct(t.lines)}`)
    console.log(`   Statements: ${pct(t.statements)}`)
    console.log(`   Functions:  ${pct(t.functions)}`)
    console.log(`   Branches:   ${pct(t.branches)}`)
    console.log('\n   Open the combined drill-down at:')
    console.log('     http://localhost:8080/coverage')
    console.log('   Or the report JSON at:')
    console.log('     http://localhost:8080/service/recording/report?track=coverage-demo')
  } else {
    console.warn('⚠️  coverage-summary.json was not produced — report may be incomplete.')
  }
}

export default globalTeardown
