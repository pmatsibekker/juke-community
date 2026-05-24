/**
 * juke-demo.spec.ts
 *
 * Visual driver for the "Juke in 15 minutes" walk-through (see
 * juke-samples/DEMO.md). Runs against a locally launched
 * juke-sample-greeting jar on localhost:8080.
 *
 * Prerequisites — start the greeting sample in another terminal first:
 *
 *   Windows (PowerShell — from repo root):
 *     $JAR = "juke-samples\juke-sample-greeting\target\juke-sample-greeting-0.0.1-SNAPSHOT.jar"
 *     java "-Djuke.enabled=true" "-Djuke.path=$env:USERPROFILE\juke-demo" `
 *          "-Djuke.zip=demo" -jar $JAR
 *
 *   macOS / Linux (from repo root):
 *     JAR="juke-samples/juke-sample-greeting/target/juke-sample-greeting-0.0.1-SNAPSHOT.jar"
 *     java -Djuke.enabled=true -Djuke.path="$HOME/juke-demo" \
 *          -Djuke.zip=demo -jar "$JAR"
 *
 * Then in this directory:
 *
 *   npx playwright test juke-demo.spec.ts --ui
 *
 * Pacing
 * ------
 * slowMo (per-action lag) is set to 2000ms, and every navigation is
 * followed by a `readableDelay` so the JSON response stays on screen
 * long enough for a viewer to read before the next nav. Each phase
 * also opens with a full-screen title card (`phaseIntro`) that names
 * the phase and previews what's about to happen — this is what makes
 * the spec viewable as a demo, not just an automated test.
 *
 * The total run time is intentionally ~2 min; tune READ_MS /
 * BETWEEN_PHASES_MS / INTRO_MS below if you want faster/slower
 * playback.
 *
 * Phase overview
 * --------------
 *   1. Record three live calls into demo.zip (with track label)
 *   2. Replay with matching inputs → COMPLETED (normal successful run)
 *   3. Stop session; verify cookies cleared; live service responds
 *   4. Replay with wrong inputs → COMPLETED_WITH_DEVIATIONS; mismatch card
 *   5. Session report: community JSON showing both runs side by side
 */

import { test, expect, BrowserContext, Page } from '@playwright/test';

test.use({
  channel: 'chrome',
  headless: false,
  launchOptions: {
    slowMo: 2000,                 // per-action delay: 2s before every click/goto
  },
});

// Each phase runs ~30–35 s at default pacing; 120 s gives comfortable
// headroom for all 5 phases.  The project-level config sets the same
// value, but this belt-and-suspenders override ensures it applies even
// when the spec is run without --project="Juke Demo".
test.setTimeout(120_000);

// ----------------------------------------------------------------------
// Pacing constants — tune to taste.
// ----------------------------------------------------------------------

/** Time the latest /greeting JSON response stays on screen before the next nav. */
const READ_MS = 3000;

/** Time the phase-intro title card stays on screen before the phase begins. */
const INTRO_MS = 4500;

/** Pause between the end of one phase and the title card of the next. */
const BETWEEN_PHASES_MS = 1500;

// ----------------------------------------------------------------------
// Config
// ----------------------------------------------------------------------

const SERVER = process.env.JUKE_SERVER ?? 'http://localhost:8080';
const TRACK  = 'demo';
const TOTAL_PHASES = 5;

// ----------------------------------------------------------------------
// Helpers
// ----------------------------------------------------------------------

/** Wait long enough for a human to read whatever's currently on screen. */
async function readableDelay(page: Page, ms: number = READ_MS) {
  await page.waitForTimeout(ms);
}

/**
 * Render a full-screen title card naming the phase about to play.
 *
 * Uses page.setContent — fast, no extra navigation, no flicker. The
 * card stays up for INTRO_MS so the viewer can read it; the next
 * page.goto in the test then replaces the card with the live response.
 */
async function phaseIntro(
  page: Page,
  phaseNumber: number,
  title: string,
  body: string,
) {
  const html = `
    <!DOCTYPE html>
    <html>
      <head>
        <meta charset="utf-8" />
        <title>Juke demo — Phase ${phaseNumber}</title>
        <style>
          * { box-sizing: border-box; margin: 0; padding: 0; }
          html, body {
            height: 100%;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: linear-gradient(135deg, #1a2a44 0%, #2d4a6e 100%);
            color: #f5f7fa;
          }
          body {
            display: flex; flex-direction: column;
            align-items: center; justify-content: center;
            text-align: center; padding: 6vw;
          }
          .stage {
            font-size: 1.1rem;
            letter-spacing: 0.3em;
            text-transform: uppercase;
            color: #8fb3d9;
            margin-bottom: 2.5rem;
          }
          h1 {
            font-size: clamp(2.2rem, 5vw, 4rem);
            font-weight: 600;
            margin-bottom: 1.5rem;
            line-height: 1.1;
          }
          p {
            font-size: clamp(1rem, 2vw, 1.4rem);
            line-height: 1.55;
            max-width: 800px;
            color: #d6e0eb;
          }
          .countdown {
            margin-top: 3rem;
            font-size: 0.95rem;
            color: #6f88a3;
            font-variant-numeric: tabular-nums;
          }
        </style>
      </head>
      <body>
        <div class="stage">Phase ${phaseNumber} of ${TOTAL_PHASES}</div>
        <h1>${title}</h1>
        <p>${body}</p>
        <div class="countdown" id="cd">resuming…</div>
        <script>
          let seconds = Math.round(${INTRO_MS} / 1000);
          const el = document.getElementById('cd');
          el.textContent = 'resuming in ' + seconds + 's';
          const tick = setInterval(() => {
            seconds -= 1;
            if (seconds <= 0) { clearInterval(tick); el.textContent = 'resuming…'; return; }
            el.textContent = 'resuming in ' + seconds + 's';
          }, 1000);
        </script>
      </body>
    </html>`;
  await page.setContent(html, { waitUntil: 'load' });
  await page.waitForTimeout(INTRO_MS);
}

/**
 * Each helper below performs one Juke control-plane or REST call and
 * asserts the response shape. The page.goto pattern keeps every action
 * visible in the URL bar so the demo viewer can follow along.
 */
async function startRecording(page: Page, track: string, label?: string) {
  const params = new URLSearchParams({ track });
  if (label) params.set('label', label);
  const res = await page.goto(`${SERVER}/service/record/start?${params}`);
  expect(res?.status(), 'record/start status').toBe(200);
  await readableDelay(page);
}

async function endRecording(page: Page) {
  // /service/record/end streams the ZIP back as the response body —
  // Playwright treats the navigation as a download, which navigates
  // away from any visible page. Catch the resulting nav exception
  // because it doesn't indicate a problem with the recording itself.
  const res = await page.goto(`${SERVER}/service/record/end`).catch(() => null);
  if (res) {
    expect(res.status(), 'record/end status').toBeLessThan(400);
  }
  await readableDelay(page);
}

async function startSession(
  page: Page,
  track: string,
  description?: string,
): Promise<{ sessionId: string }> {
  const params = new URLSearchParams({ track });
  if (description) params.set('description', description);
  const res = await page.goto(`${SERVER}/service/session/start?${params}`);
  expect(res?.status(), 'session/start status').toBe(200);
  const body = await res!.json();
  expect(body.status).toBe('active');
  expect(body.track).toBe(track);
  expect(body.sessionId).toBeTruthy();
  await readableDelay(page);
  return { sessionId: body.sessionId };
}

async function stopSession(page: Page) {
  const res = await page.goto(`${SERVER}/service/session/stop`);
  expect(res?.status(), 'session/stop status').toBe(200);
  const body = await res!.json();
  expect(body.status).toBe('stopped');
  await readableDelay(page);
}

async function fetchGreeting(page: Page, name: string): Promise<{ id: number; content: string }> {
  const res = await page.goto(`${SERVER}/greeting?name=${encodeURIComponent(name)}`);
  expect(res?.status(), `/greeting?name=${name} status`).toBe(200);
  const body = (await res!.json()) as { id: number; content: string };
  // Hold the response on screen so the viewer can read it before the
  // next nav. Without this the next page.goto in the test wipes the
  // JSON away after only ~slowMo milliseconds.
  await readableDelay(page);
  return body;
}

async function expectCookies(ctx: BrowserContext, names: string[]) {
  const cookies = await ctx.cookies();
  for (const name of names) {
    expect(
      cookies.find(c => c.name === name),
      `cookie '${name}' should be present`,
    ).toBeTruthy();
  }
}

async function expectNoCookies(ctx: BrowserContext, names: string[]) {
  const cookies = await ctx.cookies();
  for (const name of names) {
    expect(
      cookies.find(c => c.name === name),
      `cookie '${name}' should be absent`,
    ).toBeUndefined();
  }
}

/**
 * Shape of one element returned by GET /service/recording/inputs?track=…
 */
interface RecordedInput {
  file: string;
  sequence: number;
  method: string;
  parameterTypes: string[];
  arguments: unknown[];
}

/**
 * Shape of a session entry in GET /service/recording/report?track=…
 */
interface SessionReport {
  sessionId: string;
  description: string;
  startedAt: string;
  stoppedAt: string;
  callCount: number;
  overallStatus: 'COMPLETED' | 'COMPLETED_WITH_DEVIATIONS';
  calls: Array<{
    sequence: number;
    method: string;
    recordedArguments: unknown[];
    actualArguments: unknown[];
    inputMatched: boolean;
  }>;
}

interface RecordingReport {
  track: string;
  label: string;
  recordedAt: string;
  sessions: SessionReport[];
}

/**
 * Render a full-screen "mismatch report" card comparing what names the test
 * sent in this replay session against what was captured in the recording.
 * Enterprise edition: styled comparison card.
 *
 * A row is green when the sent value appears in the recorded arguments (i.e.
 * the test happened to match the recording); it is red otherwise — which is
 * exactly the scenario we want to highlight here.
 */
async function showMismatchReport(
  page: Page,
  sentNames: string[],
  recordedInputs: RecordedInput[],
) {
  const rows = sentNames.map((sent, i) => {
    const rec = recordedInputs[i];
    const recordedArg = rec ? String(rec.arguments[0] ?? '—') : '—';
    const matched = sent === recordedArg;
    return `
      <tr class="${matched ? 'match' : 'mismatch'}">
        <td class="seq">${i + 1}</td>
        <td class="sent">${sent}</td>
        <td class="recorded">${recordedArg}</td>
        <td class="badge">${matched ? '✓&nbsp;MATCH' : '✗&nbsp;MISMATCH'}</td>
      </tr>`;
  }).join('');

  const html = `<!DOCTYPE html>
<html>
  <head>
    <meta charset="utf-8"/>
    <title>Juke — Input Mismatch Report</title>
    <style>
      * { box-sizing: border-box; margin: 0; padding: 0; }
      html, body {
        height: 100%;
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        background: linear-gradient(135deg, #1a2a44 0%, #2d4a6e 100%);
        color: #f5f7fa;
      }
      body {
        display: flex; flex-direction: column;
        align-items: center; justify-content: center;
        padding: 4vw;
      }
      .header { text-align: center; margin-bottom: 2.5rem; }
      .stage {
        font-size: 1rem; letter-spacing: 0.3em;
        text-transform: uppercase; color: #8fb3d9;
        margin-bottom: 1rem;
      }
      h1 { font-size: clamp(1.8rem, 3.5vw, 2.8rem); font-weight: 600; }
      .sub {
        margin-top: .8rem;
        font-size: clamp(.9rem, 1.5vw, 1.15rem);
        color: #d6e0eb; max-width: 680px;
      }
      table {
        border-collapse: collapse;
        width: min(900px, 95vw);
        margin-top: 2rem;
        background: rgba(255,255,255,.06);
        border-radius: 12px;
        overflow: hidden;
      }
      thead th {
        padding: .9rem 1.2rem;
        text-align: left;
        font-size: .85rem;
        letter-spacing: .12em;
        text-transform: uppercase;
        color: #8fb3d9;
        background: rgba(255,255,255,.08);
      }
      tbody td {
        padding: .85rem 1.2rem;
        font-size: 1.05rem;
        border-top: 1px solid rgba(255,255,255,.07);
      }
      .seq { color: #8fb3d9; width: 3rem; text-align: center; }
      .sent    { font-weight: 500; color: #f0c060; }
      .recorded{ font-weight: 500; color: #72d4a0; }
      tr.mismatch .badge { color: #ff7070; font-weight: 700; letter-spacing: .04em; }
      tr.match   .badge  { color: #72d4a0; font-weight: 700; letter-spacing: .04em; }
      .caption {
        margin-top: 1.8rem;
        font-size: .95rem;
        color: #8fb3d9;
        max-width: 680px;
        text-align: center;
        line-height: 1.6;
      }
      .enterprise-badge {
        margin-top: 1rem;
        display: inline-block;
        padding: .35rem .9rem;
        border: 1px solid #8fb3d9;
        border-radius: 20px;
        font-size: .8rem;
        letter-spacing: .1em;
        text-transform: uppercase;
        color: #8fb3d9;
      }
    </style>
  </head>
  <body>
    <div class="header">
      <div class="stage">Phase 4 of ${TOTAL_PHASES} — Mismatch Report</div>
      <h1>Recorded Input vs What Playwright Sent</h1>
      <p class="sub">
        Juke replays by <em>sequence</em> — not by input value. The responses
        were deterministic (Alice → Bob → Charlie) even though the inputs
        were completely different. The table below shows the deviation.
      </p>
      <span class="enterprise-badge">✦ Enterprise Edition</span>
    </div>
    <table>
      <thead>
        <tr>
          <th>#</th>
          <th>Sent by test</th>
          <th>Recorded input</th>
          <th>Match?</th>
        </tr>
      </thead>
      <tbody>${rows}</tbody>
    </table>
    <p class="caption">
      To <strong>enforce</strong> input fidelity, start the server with
      <code>-Djuke.args-validation=strict</code>. In warn mode (default) the
      deviation is logged; in strict mode it surfaces as a test failure.
    </p>
  </body>
</html>`;

  await page.setContent(html, { waitUntil: 'load' });
  await page.waitForTimeout(BETWEEN_PHASES_MS * 4);
}

/**
 * Render the community-edition session report as a styled summary card.
 * The raw JSON was already visible in the browser's URL bar; this card
 * makes the data human-readable for the demo viewer.
 */
async function showReportCard(page: Page, report: RecordingReport) {
  const sessions = report.sessions ?? [];

  const sessionRows = sessions.map((s, i) => {
    const isOk = s.overallStatus === 'COMPLETED';
    const statusLabel = isOk ? '✓ COMPLETED' : '⚠ COMPLETED_WITH_DEVIATIONS';
    const statusClass = isOk ? 'ok' : 'warn';
    const callRows = (s.calls ?? []).map(c => {
      const matchClass = c.inputMatched ? 'match' : 'mismatch';
      const recArg = c.recordedArguments.length > 0 ? String(c.recordedArguments[0]) : '—';
      const actArg = c.actualArguments.length   > 0 ? String(c.actualArguments[0])   : '—';
      return `<tr class="${matchClass}">
        <td class="seq">${c.sequence}</td>
        <td>${c.method}</td>
        <td class="recorded">${recArg}</td>
        <td class="actual">${actArg}</td>
        <td class="badge ${matchClass}">${c.inputMatched ? '✓' : '✗'}</td>
      </tr>`;
    }).join('');

    return `
      <div class="session ${statusClass}">
        <div class="session-header">
          <span class="session-num">Session ${i + 1}</span>
          <span class="session-desc">${s.description || '(no description)'}</span>
          <span class="status-chip ${statusClass}">${statusLabel}</span>
        </div>
        <div class="session-meta">${s.callCount} call(s) &nbsp;·&nbsp; started ${s.startedAt.substring(11, 19)}Z &nbsp;·&nbsp; stopped ${s.stoppedAt.substring(11, 19)}Z</div>
        ${callRows ? `<table class="call-table">
          <thead><tr><th>#</th><th>Method</th><th>Recorded arg</th><th>Actual arg</th><th>Match</th></tr></thead>
          <tbody>${callRows}</tbody>
        </table>` : ''}
      </div>`;
  }).join('');

  const html = `<!DOCTYPE html>
<html>
  <head>
    <meta charset="utf-8"/>
    <title>Juke — Session Report</title>
    <style>
      * { box-sizing: border-box; margin: 0; padding: 0; }
      html, body {
        min-height: 100%;
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        background: linear-gradient(135deg, #1a2a44 0%, #2d4a6e 100%);
        color: #f5f7fa;
        padding: 3vw;
      }
      .page-header { text-align: center; margin-bottom: 2.5rem; }
      .stage {
        font-size: 1rem; letter-spacing: 0.3em;
        text-transform: uppercase; color: #8fb3d9;
        margin-bottom: .8rem;
      }
      h1 { font-size: clamp(1.8rem, 3.5vw, 2.6rem); font-weight: 600; }
      .meta { margin-top: .6rem; color: #d6e0eb; font-size: .95rem; }
      .community-badge {
        margin-top: .8rem;
        display: inline-block;
        padding: .3rem .8rem;
        border: 1px solid #6fa8dc;
        border-radius: 20px;
        font-size: .78rem;
        letter-spacing: .1em;
        text-transform: uppercase;
        color: #6fa8dc;
      }
      .session {
        background: rgba(255,255,255,.07);
        border-radius: 12px;
        padding: 1.4rem 1.6rem;
        margin-bottom: 1.4rem;
        border-left: 4px solid transparent;
      }
      .session.ok   { border-left-color: #72d4a0; }
      .session.warn { border-left-color: #f0a060; }
      .session-header {
        display: flex; align-items: center; gap: 1rem;
        flex-wrap: wrap; margin-bottom: .6rem;
      }
      .session-num  { font-weight: 700; color: #8fb3d9; font-size: 1.05rem; }
      .session-desc { flex: 1; font-size: 1.05rem; }
      .status-chip {
        padding: .25rem .7rem; border-radius: 14px;
        font-size: .8rem; font-weight: 700; letter-spacing: .04em;
      }
      .status-chip.ok   { background: rgba(114,212,160,.2); color: #72d4a0; }
      .status-chip.warn { background: rgba(240,160, 96,.2); color: #f0a060; }
      .session-meta { font-size: .85rem; color: #8fb3d9; margin-bottom: .9rem; }
      .call-table {
        width: 100%; border-collapse: collapse;
        background: rgba(255,255,255,.04);
        border-radius: 8px; overflow: hidden;
      }
      .call-table thead th {
        padding: .55rem .9rem; font-size: .78rem;
        letter-spacing: .1em; text-transform: uppercase;
        color: #8fb3d9; background: rgba(255,255,255,.06);
        text-align: left;
      }
      .call-table tbody td {
        padding: .6rem .9rem; font-size: .95rem;
        border-top: 1px solid rgba(255,255,255,.06);
      }
      .seq     { color: #8fb3d9; text-align: center; width: 2.5rem; }
      .recorded{ color: #72d4a0; font-weight: 500; }
      .actual  { color: #f0c060; font-weight: 500; }
      .badge.match    { color: #72d4a0; font-weight: 700; }
      .badge.mismatch { color: #ff7070; font-weight: 700; }
    </style>
  </head>
  <body>
    <div class="page-header">
      <div class="stage">Phase ${TOTAL_PHASES} of ${TOTAL_PHASES} — Session Report</div>
      <h1>${report.label || report.track}</h1>
      <p class="meta">Track: <strong>${report.track}</strong> &nbsp;·&nbsp; Recorded: ${report.recordedAt ? report.recordedAt.substring(0, 19).replace('T', ' ') + 'Z' : '—'}</p>
      <span class="community-badge">◎ Community Edition</span>
    </div>
    ${sessionRows || '<p style="text-align:center;color:#8fb3d9">No completed sessions recorded yet.</p>'}
  </body>
</html>`;

  await page.setContent(html, { waitUntil: 'load' });
  // Hold the report on screen long enough for the viewer to digest it.
  await page.waitForTimeout(BETWEEN_PHASES_MS * 6);
}

// ----------------------------------------------------------------------
// The walk-through. One serial describe so the demo plays as a single
// continuous narrative — record, replay, stop, fall through to live.
// ----------------------------------------------------------------------

test.describe.serial('Juke demo — full record / replay walk-through', () => {

  // A single shared browser context + page for the whole walk-through.
  // The demo plays as one continuous narrative: the JUKE_SESSION_ID /
  // JUKE_TRACK cookies set when a session starts in Phase 2 must still
  // be in the cookie jar when Phase 3 stops that same session.
  // Playwright's default per-test fixtures hand each test a fresh
  // context, which silently drops the session cookie between phases —
  // so Phase 3's stop would never reach Phase 2's session and that
  // session would never enter the completed-session history. We manage
  // one context here so the session lifecycle spans phases correctly.
  let context: BrowserContext;
  let page: Page;

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext();
    page = await context.newPage();
  });

  test.afterAll(async () => {
    await context.close();
  });

  test('Phase 1 — record three calls into demo.zip', async () => {
    await phaseIntro(
      page,
      1,
      'Recording three live calls',
      'About to call /service/record/start with a track label, then drive three ' +
      '/greeting calls with names Alice, Bob and Charlie. Each call lands in the ' +
      'ZIP track at sequence 1, 2 and 3 — watch the JSON response in the page body.',
    );

    await startRecording(page, TRACK, 'Greetings — Basic Flow Test');

    const alice = await fetchGreeting(page, 'Alice');
    expect(alice).toMatchObject({ id: 1, content: 'Hello, Alice!' });

    const bob = await fetchGreeting(page, 'Bob');
    expect(bob).toMatchObject({ id: 2, content: 'Hello, Bob!' });

    const charlie = await fetchGreeting(page, 'Charlie');
    expect(charlie).toMatchObject({ id: 3, content: 'Hello, Charlie!' });

    await endRecording(page);
    await page.waitForTimeout(BETWEEN_PHASES_MS);
  });

  test('Phase 2 — normal successful replay: matching inputs, recorded outputs', async () => {
    await phaseIntro(
      page,
      2,
      'Normal successful deterministic replay',
      'Starting a session with description "Normal successful deterministic replay". ' +
      'Calling /greeting with the same names (Alice, Bob, Charlie) that were recorded — ' +
      'each response should match exactly. This is what a clean passing run looks like.',
    );

    const { sessionId } = await startSession(
      page, TRACK, 'Normal successful deterministic replay',
    );
    expect(sessionId).toBeTruthy();

    // DevTools → Application → Cookies should show both of these now.
    await expectCookies(context, ['JUKE_SESSION_ID', 'JUKE_TRACK']);

    // Send the same names that were recorded — all three should match.
    const alice = await fetchGreeting(page, 'Alice');
    expect(alice, 'call 1 returns recorded Alice response').toMatchObject({
      id: 1, content: 'Hello, Alice!',
    });

    const bob = await fetchGreeting(page, 'Bob');
    expect(bob, 'call 2 returns recorded Bob response').toMatchObject({
      id: 2, content: 'Hello, Bob!',
    });

    const charlie = await fetchGreeting(page, 'Charlie');
    expect(charlie, 'call 3 returns recorded Charlie response').toMatchObject({
      id: 3, content: 'Hello, Charlie!',
    });

    await page.waitForTimeout(BETWEEN_PHASES_MS);
  });

  test('Phase 3 — stop session, verify cookies cleared and live service returns', async () => {
    await phaseIntro(
      page,
      3,
      'Stopping the session — live service returns',
      'About to call /service/session/stop. The Set-Cookie header from the ' +
      'server clears JUKE_SESSION_ID + JUKE_TRACK from the cookie jar; ' +
      'after that the next /greeting call falls through to the real upstream ' +
      'service and returns "Hello, Zelda!" with a fresh id.',
    );

    await stopSession(page);
    await expectNoCookies(context, ['JUKE_SESSION_ID', 'JUKE_TRACK']);

    // No session, no global replay → falls through to the live service.
    // The live counter keeps incrementing across the JVM's lifetime, so we
    // only assert on the content (not the id).
    const live = await fetchGreeting(page, 'Zelda');
    expect(live.content).toBe('Hello, Zelda!');
    expect(live.id).toBeGreaterThan(0);
  });

  test('Phase 4 — mismatch report: prove sequence beats input', async () => {
    await phaseIntro(
      page,
      4,
      'Input Mismatch Report',
      'Start a fresh session described as "Run with unexpected inputs" and send three ' +
      'calls with deliberately wrong names. Juke still returns the recorded ' +
      'Alice / Bob / Charlie responses — sequence wins over input. Then we ' +
      'fetch the recorded inputs and render a side-by-side comparison card.',
    );

    // Names that deliberately differ from the recording (Alice/Bob/Charlie).
    const sentNames = ['WrongName_A', 'WrongName_B', 'WrongName_C'];

    const { sessionId } = await startSession(page, TRACK, 'Run with unexpected inputs');
    expect(sessionId).toBeTruthy();
    await expectCookies(context, ['JUKE_SESSION_ID', 'JUKE_TRACK']);

    // Despite the wrong inputs, responses must match the recorded sequence.
    const r1 = await fetchGreeting(page, sentNames[0]);
    expect(r1, 'wrong input, call 1 → still returns recorded Alice').toMatchObject({
      id: 1, content: 'Hello, Alice!',
    });

    const r2 = await fetchGreeting(page, sentNames[1]);
    expect(r2, 'wrong input, call 2 → still returns recorded Bob').toMatchObject({
      id: 2, content: 'Hello, Bob!',
    });

    const r3 = await fetchGreeting(page, sentNames[2]);
    expect(r3, 'wrong input, call 3 → still returns recorded Charlie').toMatchObject({
      id: 3, content: 'Hello, Charlie!',
    });

    // ── Fetch the recorded inputs from the comparison endpoint ──────────────
    // Navigate so the raw JSON is visible in the URL bar before the card.
    const inputsRes = await page.goto(`${SERVER}/service/recording/inputs?track=${TRACK}`);
    expect(inputsRes?.status(), '/service/recording/inputs status').toBe(200);
    const recordedInputs = (await inputsRes!.json()) as RecordedInput[];
    expect(recordedInputs.length, 'should have 3 recorded arg entries').toBe(3);
    await readableDelay(page);

    // ── Enterprise: render the styled side-by-side mismatch card ────────────
    await showMismatchReport(page, sentNames, recordedInputs);

    await stopSession(page);
    await expectNoCookies(context, ['JUKE_SESSION_ID', 'JUKE_TRACK']);
  });

  test('Phase 5 — session report: community edition JSON', async () => {
    await phaseIntro(
      page,
      5,
      'Session Report — Community Edition',
      'Fetching the full session report for this track. Two completed sessions ' +
      'should appear: the normal successful run (COMPLETED) and the run with ' +
      'unexpected inputs (COMPLETED_WITH_DEVIATIONS). The report includes the ' +
      'track label, each session\'s description, call count, and per-call ' +
      'argument comparison — all as formatted JSON.',
    );

    // ── Navigate to the report endpoint — viewer sees raw pretty JSON ──────
    const res = await page.goto(`${SERVER}/service/recording/report?track=${TRACK}`);
    expect(res?.status(), '/service/recording/report status').toBe(200);
    const report = (await res!.json()) as RecordingReport;

    // ── Validate the report structure ──────────────────────────────────────
    expect(report.track, 'report.track').toBe(TRACK);
    expect(report.label, 'recording label').toBe('Greetings — Basic Flow Test');
    expect(report.sessions, 'should have 2 completed sessions').toHaveLength(2);

    // Session 1: normal successful replay
    const s1 = report.sessions[0];
    expect(s1.description).toBe('Normal successful deterministic replay');
    expect(s1.callCount).toBe(3);
    expect(s1.overallStatus, 'normal run should be COMPLETED').toBe('COMPLETED');
    expect(s1.calls[0].inputMatched).toBe(true);
    expect(s1.calls[1].inputMatched).toBe(true);
    expect(s1.calls[2].inputMatched).toBe(true);

    // Session 2: run with unexpected inputs
    const s2 = report.sessions[1];
    expect(s2.description).toBe('Run with unexpected inputs');
    expect(s2.callCount).toBe(3);
    expect(s2.overallStatus, 'mismatch run should be COMPLETED_WITH_DEVIATIONS')
      .toBe('COMPLETED_WITH_DEVIATIONS');
    expect(s2.calls[0].inputMatched).toBe(false);
    expect(s2.calls[1].inputMatched).toBe(false);
    expect(s2.calls[2].inputMatched).toBe(false);

    await readableDelay(page);

    // ── Community: render a styled summary card ────────────────────────────
    await showReportCard(page, report);
  });
});
