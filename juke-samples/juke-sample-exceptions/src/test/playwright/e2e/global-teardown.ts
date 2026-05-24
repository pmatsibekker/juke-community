import * as fs from 'fs'
import * as os from 'os'
import * as path from 'path'
// istanbul-lib-* ship as CJS without TS types in the playwright/nyc tree.
// eslint-disable-next-line @typescript-eslint/no-var-requires
const libCoverage = require('istanbul-lib-coverage')
// eslint-disable-next-line @typescript-eslint/no-var-requires
const libReport   = require('istanbul-lib-report')
// eslint-disable-next-line @typescript-eslint/no-var-requires
const reports     = require('istanbul-reports')

/**
 * Turns the harvested window.__coverage__ data into:
 *   - coverage-summary.json — read by /service/coverage/ui for percentages
 *   - index.html (+ assets) — the drill-down shown in the coverage popup's
 *     "Coverage report" tab (iframe → /coverage/ui/index.html)
 *
 * Output dir: ~/juke-demo/coverage/ui — where UiCoverageService reads from and
 * the resource handler maps to /coverage/ui/**.
 *
 * We drive the istanbul libraries directly rather than shelling out to
 * `nyc report`, which mis-resolves Windows absolute paths and reports 0%.
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

  const map = libCoverage.createCoverageMap({})
  for (const fileName of files) {
    try {
      const raw = JSON.parse(fs.readFileSync(path.join(nycOutput, fileName), 'utf-8'))
      map.merge(raw)
    } catch (e) {
      console.warn(`  ⚠️  Could not parse ${fileName} — skipping (${(e as Error).message})`)
    }
  }

  const ctx = libReport.createContext({
    dir: reportDir,
    defaultSummarizer: 'nested',
    coverageMap: map,
  })

  reports.create('html', { subdir: '' }).execute(ctx)
  reports.create('json-summary').execute(ctx)
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
    console.log('\n   Open the coverage popup in the app, or the report at:')
    console.log('     http://localhost:8080/coverage/ui/index.html')
  } else {
    console.warn('⚠️  coverage-summary.json was not produced — report may be incomplete.')
  }
}

export default globalTeardown
