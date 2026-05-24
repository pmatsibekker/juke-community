/**
 * greeting-ui.spec.ts
 *
 * Functional UI-coverage driver for the greeting React SPA.
 *
 * Unlike juke-demo.spec.ts — which exercises the /service/* REST surface by
 * navigating straight to JSON endpoints — this spec loads the actual React
 * application served at http://localhost:8080/ and drives the rendered form:
 * it types a name, clicks Submit, and reads the response. That makes the
 * app's JavaScript execute in the browser, which is what produces
 * window.__coverage__ data.
 *
 * Coverage is collected automatically: `test` is imported from
 * coverage-fixture.ts, whose auto fixture harvests window.__coverage__ after
 * each test into .nyc_output/. The run's globalTeardown (see
 * playwright.config.ts) then turns that data into an nyc report.
 *
 * Prerequisites:
 *   1. Build the server with the instrumented SPA:  mvn package -Pcoverage
 *   2. Start it:  demo-start-server
 *   3. Run:  npx playwright test --project="Coverage" e2e/greeting-ui.spec.ts
 *
 * If the server is running a plain (uninstrumented) build the spec still
 * passes as a UI smoke test — there is simply no window.__coverage__ to
 * harvest, and the teardown reports that no coverage was collected.
 */
import { test, expect } from './coverage-fixture';

const SERVER = process.env.JUKE_SERVER ?? 'http://localhost:8080';

test.describe('Greeting SPA — UI coverage flows', () => {

  // A handful of distinct names so the input handler, the Submit click
  // handler, the axios call and the response render are all exercised.
  for (const name of ['Ada', 'Linus', 'Grace']) {
    test(`greet ${name}`, async ({ page }) => {
      await page.goto(`${SERVER}/`);

      // Drive the rendered React form.
      await page.locator('#inputName').fill(name);
      await page.getByRole('button', { name: 'Submit' }).click();

      // The SPA writes the greeting service response into #response;
      // the web-first assertion retries until the async call has resolved.
      await expect(page.locator('#response')).not.toBeEmpty();
    });
  }
});
