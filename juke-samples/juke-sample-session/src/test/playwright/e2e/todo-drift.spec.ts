/**
 * todo-drift.spec.ts
 *
 * Visual driver for the juke-sample-todo "session drift detection" UI.
 *
 * Prerequisite — start the todo sample in another terminal first:
 *   juke-samples\juke-sample-todo\demo-start-server.bat   (Windows)
 *   ./juke-samples/juke-sample-todo/demo-start-server.ps1 (PowerShell)
 *
 * Then from this directory:
 *   npx playwright test --project="Todo Drift" e2e/todo-drift.spec.ts --headed
 *
 * What it does — mirrors the curl drift section, but driven from the UI:
 *   1. Click "Start recording" → POST two todos ("Buy milk", "Buy bread") → "Stop recording"
 *   2. Click "Start replay session" → POST two todos with the SECOND ONE DIFFERENT
 *      ("Buy milk", "Buy oats") → "Stop session & show report"
 *   3. Asserts the report panel renders:
 *        - overallStatus badge "COMPLETED_WITH_DEVIATIONS"
 *        - call #2 row has the diff-bad highlight
 *
 * Headless verification:  JUKE_HEADLESS=1 npx playwright test e2e/todo-drift.spec.ts
 */

import { test, expect } from '@playwright/test';

// Pacing: headless verification runs at full speed (slowMo=0).
// Headed/demo runs at slowMo=1500 so a human can follow each transition
// (fill → click → mode-pill flip → report panel populate).
test.use({
  channel: 'chrome',
  headless: !!process.env.JUKE_HEADLESS,
  launchOptions: {
    slowMo: process.env.JUKE_HEADLESS ? 0 : 1500,
  },
});

const BASE = 'http://localhost:8080';
const TRACK = 'todo-ui';

test('todo drift visible in session run report', async ({ page, request }) => {
  // Clean slate: clear any prior recording + delete any pre-existing todos so the
  // baseline starts at sequence 1. (Reset endpoint isn't exposed; iterate /todos.)
  const existing = await request.get(`${BASE}/todos`).then(r => r.json());
  for (const t of existing) await request.delete(`${BASE}/todos/${t.id}`);

  await page.goto(BASE);
  await expect(page.locator('h1')).toContainText('ToDo');

  // Helper: click Add and wait for the inline async addTodo() to finish.
  // The page clears #newTitle after the POST resolves and before refreshTodos(),
  // so an empty input is a reliable "POST finished" signal. Then networkidle
  // covers the GET /todos refresh that follows.
  const newTitle = page.getByTestId('new-title');
  const addAndWait = async (title: string) => {
    await newTitle.fill(title);
    await page.getByTestId('btn-add').click();
    await expect(newTitle).toHaveValue('', { timeout: 5_000 });
    await page.waitForLoadState('networkidle');
  };

  // ── Phase 1: record baseline ────────────────────────────────────────────────
  await page.getByRole('button', { name: /Start recording/ }).click();
  await expect(page.locator('#modePill')).toHaveText('recording');

  for (const title of ['Buy milk', 'Buy bread']) {
    await addAndWait(title);
    await expect(page.getByTestId('todo-list')).toContainText(title);
  }

  await page.getByRole('button', { name: /Stop recording/ }).click();
  await expect(page.locator('#modePill')).toHaveText('live');

  // ── Phase 2: replay session with INCONSISTENT second title ──────────────────
  await page.getByRole('button', { name: /Start replay session/ }).click();
  await expect(page.locator('#modePill')).toHaveText('session');

  // First call matches the recording exactly.
  await addAndWait('Buy milk');
  // Second call DIFFERS — recorded was "Buy bread", UI sends "Buy oats".
  await addAndWait('Buy oats');

  await page.getByRole('button', { name: /Stop session/ }).click();
  await expect(page.locator('#modePill')).toHaveText('live');

  // ── Phase 3: assert the report panel surfaces the drift ─────────────────────
  // The report is auto-fetched after sessionStop(); the badge is the user-visible
  // signal that the @Juke seam detected the inconsistent input.
  const lastStatus = page.getByTestId('overall-status').last();
  await expect(lastStatus).toHaveText('COMPLETED_WITH_DEVIATIONS', { timeout: 5_000 });

  // The drift row is the 2nd createTodo call — recorded "Buy bread", actual "Buy oats".
  // testid format is `call-{method}-{sequence}` so this picks the right row even when
  // other methods (getAllTodos, etc.) also have sequence=2.
  const driftRow = page.getByTestId('call-createTodo-2').last();
  await expect(driftRow).toHaveClass(/diff-bad/);
  await expect(driftRow).toContainText('Buy bread');   // recorded
  await expect(driftRow).toContainText('Buy oats');    // actual
  await expect(driftRow.getByTestId('badge-createTodo-2')).toHaveText('drift');

  // Headed demo: pause briefly with the drift highlighted so a human watcher
  // can read the result before the browser closes.
  if (!process.env.JUKE_HEADLESS) await page.waitForTimeout(3_000);
});
