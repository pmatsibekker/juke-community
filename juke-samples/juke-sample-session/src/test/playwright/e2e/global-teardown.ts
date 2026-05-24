import { execSync } from 'child_process';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';

/**
 * Playwright global teardown — after all E2E tests finish, turn any collected
 * Istanbul coverage data into an nyc report.
 *
 * Per-test coverage (window.__coverage__) is written into .nyc_output/ by
 * coverage-fixture.ts. Here that accumulated data is rendered as:
 *   - text          (console summary)
 *   - html          (drill-down report — index.html)
 *   - json-summary  (coverage-summary.json — machine-readable totals)
 *
 * The report is written into the Juke recording directory at
 *   <home>/juke-demo/coverage/ui
 * which mirrors the server-side juke.coverage.ui-report-dir default, so the
 * juke-coverage module can serve the HTML at /coverage/ui/** and read
 * coverage-summary.json for the /service/coverage/ui endpoint.
 *
 * No-op when no coverage data was gathered (e.g. the SPA was not built with
 * `mvn package -Pcoverage`, so window.__coverage__ was never defined).
 */
async function globalTeardown() {
  const playwrightDir = path.join(__dirname, '..');
  const nycOutput = path.join(playwrightDir, '.nyc_output');

  if (!fs.existsSync(nycOutput) || fs.readdirSync(nycOutput).length === 0) {
    console.log('\nℹ️  No UI coverage data collected — was the server built with '
      + '`mvn package -Pcoverage` and driven via greeting-ui.spec.ts?');
    return;
  }

  const reportDir = path.join(os.homedir(), 'juke-demo', 'coverage', 'ui');
  console.log('\n📊 Generating UI coverage report → ' + reportDir);
  try {
    execSync(
      'npx nyc report --reporter=text --reporter=html --reporter=json-summary '
        + '--report-dir="' + reportDir + '"',
      { cwd: playwrightDir, stdio: 'inherit' },
    );
    console.log('✅ UI coverage report generated (index.html + coverage-summary.json)');
  } catch (e) {
    console.warn('⚠️  Failed to generate UI coverage report:', (e as Error).message);
  }
}

export default globalTeardown;
