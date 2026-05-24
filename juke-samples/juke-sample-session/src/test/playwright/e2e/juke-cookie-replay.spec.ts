/**
 * juke-cookie-replay.spec.ts
 *
 * Validates that cookie-based Juke replay mode is properly session-isolated.
 * Runs in a real visible Google Chrome window so every navigation is observable.
 *
 * Prerequisites — the session sample must be running before this suite:
 *
 *   Windows (PowerShell — run from repo root):
 *     $JAR      = "juke-samples\juke-sample-session\target\juke-sample-session-0.0.1-SNAPSHOT.jar"
 *     $JUKEPATH = "juke-samples\juke-sample-session\src\test\playwright\test-resources"
 *     java "-Djuke=replay" "-Djuke.path=$JUKEPATH" "-Djuke.zip=juke-sample-ui" -jar $JAR
 *
 *   macOS / Linux (run from repo root):
 *     JAR="juke-samples/juke-sample-session/target/juke-sample-session-0.0.1-SNAPSHOT.jar"
 *     JUKEPATH="juke-samples/juke-sample-session/src/test/playwright/test-resources"
 *     java -Djuke=replay -Djuke.path="$JUKEPATH" -Djuke.zip=juke-sample-ui -jar "$JAR"
 *
 * Or set the JUKE_SERVER env var to override the default http://localhost:8080.
 *
 * ZIP recordings used (in test-resources/, kept under the original juke-sample-ui name
 * because that's the track name embedded inside the ZIP entries themselves):
 *   juke-sample-ui.zip   → call-1: "Hello, Evan!"   | call-2: "Hello, Pavel!"
 *   juke-sample-ui-2.zip → call-1: "Hello, Evan!"   | call-2: "Hello, Viane!"
 *
 * Cookie protocol (set by /service/session/start, read by JukeCookieFilter):
 *   JUKE_SESSION_ID  — UUID of the active playback session
 *   JUKE_TRACK       — name of the ZIP recording to replay from
 */

import { test, expect, BrowserContext, Page } from '@playwright/test';

// ---------------------------------------------------------------------------
// Run this suite in real Google Chrome, visibly, so every step can be watched.
// slowMo adds a short pause between actions to make the navigation readable.
// ---------------------------------------------------------------------------
test.use({
  channel: 'chrome',      // use the installed Google Chrome, not bundled Chromium
  headless: false,        // open visible Chrome windows
  launchOptions: {
    slowMo: 1000,         // 1 000 ms between actions — gives the eye time to follow
  },
});

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

const SERVER = process.env.JUKE_SERVER ?? 'http://localhost:8080';

const TRACK_1 = 'juke-sample-ui';
const TRACK_2 = 'juke-sample-ui-2';

/** Exact JSON the server must return when replaying track 1 in sequence. */
const TRACK_1_CALL_1 = { id: 1, content: 'Hello, Evan!' };
const TRACK_1_CALL_2 = { id: 2, content: 'Hello, Pavel!' };

/** Exact JSON the server must return when replaying track 2 in sequence. */
const TRACK_2_CALL_1 = { id: 1, content: 'Hello, Evan!' };
const TRACK_2_CALL_2 = { id: 2, content: 'Hello, Viane!' };

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Navigates the Chrome window to /service/session/start and parses the response.
 * The server writes JUKE_SESSION_ID + JUKE_TRACK as Set-Cookie headers;
 * Chrome stores them automatically (visible in DevTools → Application → Cookies).
 */
async function startSession(page: Page, track: string) {
  const response = await page.goto(`${SERVER}/service/session/start?track=${track}`);
  expect(response?.status(), `session/start HTTP status`).toBe(200);
  const body = await response!.json();
  expect(body.status, `session/start body.status`).toBe('active');
  expect(body.track).toBe(track);
  expect(body.sessionId).toBeTruthy();
  return body as { sessionId: string; track: string; status: string };
}

/**
 * Navigates the Chrome window to /service/session/stop and asserts the response.
 * The server responds with Set-Cookie: ...; Max-Age=0 which tells Chrome to
 * delete the Juke cookies immediately.
 */
async function stopSession(page: Page) {
  const response = await page.goto(`${SERVER}/service/session/stop`);
  expect(response?.status()).toBe(200);
  const body = await response!.json();
  expect(body.status).toBe('stopped');
}

/**
 * Navigates the Chrome window to /session-greeting and parses the JSON response.
 * Unlike the legacy /greeting endpoint, /session-greeting uses @Juke("none") —
 * a lazy per-request proxy that routes to SessionAwareReplayHandler when Juke
 * session cookies are present, or falls through to the real service otherwise.
 */
async function navigateGreeting(page: Page, name: string): Promise<{ id: number; content: string }> {
  const response = await page.goto(`${SERVER}/session-greeting?name=${encodeURIComponent(name)}`);
  expect(response?.status(), `/session-greeting HTTP status`).toBe(200);
  return response!.json() as Promise<{ id: number; content: string }>;
}

/**
 * Verifies that the named cookie exists in the context's cookie jar.
 * Equivalent to opening DevTools → Application → Cookies in a real browser.
 */
async function expectCookie(ctx: BrowserContext, name: string, expectedValue?: string) {
  const cookies = await ctx.cookies();
  const cookie = cookies.find(c => c.name === name);
  expect(cookie, `cookie '${name}' must be present`).toBeTruthy();
  if (expectedValue !== undefined) {
    expect(cookie!.value, `cookie '${name}' value`).toBe(expectedValue);
  }
  return cookie!;
}

/**
 * Verifies that the named cookie has been deleted from the context.
 */
async function expectNoCookie(ctx: BrowserContext, name: string) {
  const cookies = await ctx.cookies();
  expect(cookies.find(c => c.name === name), `cookie '${name}' must be absent`).toBeUndefined();
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

test.describe('Juke cookie-based replay — session isolation (Chrome)', () => {

  // ─── Test 1 ────────────────────────────────────────────────────────────────
  // Mirrors MANUAL_TESTING.md Steps B–G (Chrome section).
  // A single visible Chrome window:
  //   - starts a Juke session (cookies set)
  //   - makes two replayed calls (served from ZIP)
  //   - stops the session (cookies cleared)
  //   - confirms pass-through is restored

  test('Normal browser (with Juke cookies) receives deterministic recorded responses', async ({ browser }) => {
    const ctx: BrowserContext = await browser.newContext();
    const page: Page = await ctx.newPage();

    try {
      // ── Step B: verify pass-through before session ──────────────────────
      const preCall = await navigateGreeting(page, 'World');
      // Real service responds — id increments from AtomicLong, content echoes the name
      expect(preCall.content).toBe('Hello, World!');
      console.log(`  ✔  Pre-session (real service): ${JSON.stringify(preCall)}`);
      await page.waitForTimeout(1500); // pause so the result is readable

      // ── Step C: start session → cookies appear in Chrome ────────────────
      const session = await startSession(page, TRACK_1);
      console.log(`  ✔  Session started: ${session.sessionId} (track: ${TRACK_1})`);
      await page.waitForTimeout(1500); // pause so the session JSON is readable

      const sessionCookie = await expectCookie(ctx, 'JUKE_SESSION_ID');
      await expectCookie(ctx, 'JUKE_TRACK', TRACK_1);
      console.log(`  ✔  Cookies set: JUKE_SESSION_ID=${sessionCookie.value.slice(0, 8)}...  JUKE_TRACK=${TRACK_1}`);
      await page.waitForTimeout(1500); // pause so cookies can be inspected in DevTools

      // ── Step E: confirm session/status shows active ──────────────────────
      const statusResp = await page.goto(`${SERVER}/service/session/status`);
      const statusBody = await statusResp!.json();
      expect(statusBody.status).toBe('active');
      expect(statusBody.sessionId).toBe(session.sessionId);
      await page.waitForTimeout(1500); // pause so the status response is readable

      // ── Step D: call 1 — must match ZIP recording entry 1 ────────────────
      const call1 = await navigateGreeting(page, 'Evan');
      expect(call1).toEqual(TRACK_1_CALL_1);
      console.log(`  ✔  Call 1 matched recording: ${JSON.stringify(call1)}`);
      await page.waitForTimeout(1500); // pause to read the first recorded response

      // ── Step D: call 2 — sequence counter advances to entry 2 ────────────
      const call2 = await navigateGreeting(page, 'Pavel');
      expect(call2).toEqual(TRACK_1_CALL_2);
      console.log(`  ✔  Call 2 matched recording: ${JSON.stringify(call2)}`);
      await page.waitForTimeout(1500); // pause to read the second recorded response

      // ── Step F: stop session → Chrome deletes both cookies ────────────────
      await stopSession(page);
      console.log(`  ✔  Session stopped`);
      await expectNoCookie(ctx, 'JUKE_SESSION_ID');
      await expectNoCookie(ctx, 'JUKE_TRACK');
      console.log(`  ✔  Cookies cleared`);
      await page.waitForTimeout(1500); // pause to observe the cookie deletion

      // ── Step G: verify pass-through is restored ───────────────────────────
      const afterCall = await navigateGreeting(page, 'World');
      expect(afterCall.content).toBe('Hello, World!');
      // Must NOT be a recorded response (real service, not ZIP)
      expect(afterCall).not.toEqual(TRACK_1_CALL_1);
      expect(afterCall).not.toEqual(TRACK_1_CALL_2);
      console.log(`  ✔  Post-session (real service restored): ${JSON.stringify(afterCall)}`);
      await page.waitForTimeout(1500); // pause so the final result is readable

    } finally {
      await ctx.close();
    }
  });

  // ─── Test 2 ────────────────────────────────────────────────────────────────
  // Mirrors MANUAL_TESTING.md Step H (incognito window).
  // browser.newContext() gives a completely empty cookie store —
  // the equivalent of opening a new Chrome incognito window.

  test('Incognito window (no cookies) bypasses replay and hits the real service', async ({ browser }) => {
    const incognitoCtx: BrowserContext = await browser.newContext();
    const incognitoPage: Page = await incognitoCtx.newPage();

    try {
      // No session started → status must report no_session immediately.
      const statusResp = await incognitoPage.goto(`${SERVER}/service/session/status`);
      const status = await statusResp!.json();
      expect(status.status).toBe('no_session');
      console.log(`  ✔  Incognito: no session active (as expected)`);
      await incognitoPage.waitForTimeout(1500); // pause so the status is readable

      // No Juke cookies → /session-greeting routes to real GreetingServiceImpl.
      const call = await navigateGreeting(incognitoPage, 'World');
      expect(call.content).toBe('Hello, World!');
      // Must not match any ZIP recording
      expect(call.content).not.toBe(TRACK_1_CALL_1.content); // not "Hello, Evan!"
      expect(call.content).not.toBe(TRACK_1_CALL_2.content); // not "Hello, Pavel!"
      console.log(`  ✔  Incognito: real service responded: ${JSON.stringify(call)}`);
      await incognitoPage.waitForTimeout(1500); // pause so the final result is readable

    } finally {
      await incognitoCtx.close();
    }
  });

  // ─── Test 3 ────────────────────────────────────────────────────────────────
  // Mirrors MANUAL_TESTING.md § Two-browser test.
  // Two visible Chrome windows open simultaneously, each on a different track.
  // Proves that session counters and cookie stores are fully independent.

  test('Two concurrent Chrome windows on different tracks replay independently', async ({ browser }) => {
    // Window A = "normal browser" on track 1
    const ctxA: BrowserContext = await browser.newContext();
    const pageA: Page = await ctxA.newPage();

    // Window B = "incognito / second browser profile" on track 2
    const ctxB: BrowserContext = await browser.newContext();
    const pageB: Page = await ctxB.newPage();

    try {
      // Start independent sessions on each window.
      const sessionA = await startSession(pageA, TRACK_1);
      const sessionB = await startSession(pageB, TRACK_2);

      // Sessions must have distinct IDs.
      expect(sessionA.sessionId).not.toBe(sessionB.sessionId);
      console.log(`  ✔  Window A session: ${sessionA.sessionId.slice(0, 8)}... (track: ${TRACK_1})`);
      console.log(`  ✔  Window B session: ${sessionB.sessionId.slice(0, 8)}... (track: ${TRACK_2})`);
      await pageA.waitForTimeout(1500); // pause to observe both sessions started

      // Confirm cookies in each window reflect their own track.
      await expectCookie(ctxA, 'JUKE_TRACK', TRACK_1);
      await expectCookie(ctxB, 'JUKE_TRACK', TRACK_2);
      await pageA.waitForTimeout(1500); // pause to inspect cookies in each window

      // ── Call 1: both tracks start with "Hello, Evan!" ─────────────────────
      const a1 = await navigateGreeting(pageA, 'any');
      const b1 = await navigateGreeting(pageB, 'any');
      expect(a1).toEqual(TRACK_1_CALL_1);
      expect(b1).toEqual(TRACK_2_CALL_1);
      console.log(`  ✔  Window A call 1: ${JSON.stringify(a1)}`);
      console.log(`  ✔  Window B call 1: ${JSON.stringify(b1)}`);
      await pageA.waitForTimeout(1500); // pause so both call-1 results can be compared

      // ── Call 2: tracks diverge — "Pavel" vs "Viane" ───────────────────────
      const a2 = await navigateGreeting(pageA, 'any');
      const b2 = await navigateGreeting(pageB, 'any');
      expect(a2).toEqual(TRACK_1_CALL_2); // "Hello, Pavel!"
      expect(b2).toEqual(TRACK_2_CALL_2); // "Hello, Viane!"
      console.log(`  ✔  Window A call 2: ${JSON.stringify(a2)}`);
      console.log(`  ✔  Window B call 2: ${JSON.stringify(b2)}`);
      await pageA.waitForTimeout(1500); // pause so the divergence is visible

      // Verify status endpoints still show independent sessions.
      const sA = await (await pageA.goto(`${SERVER}/service/session/status`))!.json();
      const sB = await (await pageB.goto(`${SERVER}/service/session/status`))!.json();
      expect(sA.track).toBe(TRACK_1);
      expect(sB.track).toBe(TRACK_2);
      expect(sA.sessionId).not.toBe(sB.sessionId);
      console.log(`  ✔  Session IDs are distinct (isolation confirmed)`);
      await pageA.waitForTimeout(1500); // pause before tearing down

      // Stop both sessions.
      await stopSession(pageA);
      await stopSession(pageB);

    } finally {
      await ctxA.close();
      await ctxB.close();
    }
  });
});

