# Lessons Learned — Authoring Juke Sample Projects

Hard-won notes from building `juke-sample-exceptions` (record → replay → replay+delay →
replay+exception, with a coverage popup). Most of the time lost on that build went to
**packaging staleness** and **unverified framework assumptions**, not to writing features.
And the biggest *credibility* risk was asserting framework behavior I'd only **read in
source, not run** — see §2.0. Read this before starting the next sample.

---

## 1. Build & packaging (this is where the time goes)

### 1.1 Generate the SPA into `target/`, never a source-tree `dist/`
- A Vite `outDir: 'dist'` under `src/main/web-app/` is **not** cleaned by `mvn clean`, so a
  stale bundle silently survives rebuilds.
- **Do:** point Vite at `outDir: '../../../target/classes/static'` so the bundle lands on the
  classpath, is served at `/` straight from the jar, and is wiped by `mvn clean`. This removes
  the separate `copy-resources` step entirely.

### 1.2 Spring Boot `repackage` is incremental — it will embed a STALE dependency
- `spring-boot:repackage` skips when the main artifact is already a Boot jar. Across many
  `mvn package` runs the fat jar was **never rewritten** (its mtime never changed) and kept
  embedding a weeks-old `juke-remix-rest-service` jar — so a framework fix that was correct in
  source, in `target/classes`, and in `.m2` still wasn't in the running app.
- **Do:** run `mvn clean package` (not bare `package`) whenever a dependency changed. After
  building, **verify the embedded artifact actually contains your change** before testing:
  ```bash
  unzip -p target/<app>.jar BOOT-INF/lib/<dep>-<ver>.jar > /tmp/d.jar
  unzip -p /tmp/d.jar <pkg>/<ChangedClass>.class | grep -a <a-string-literal-you-added>
  ```
  `public static final String` constants are inlined into the `.class`, so grepping the class
  for a literal you added is a fast "is my change really in here?" check.

### 1.3 A failed `mvn install` does NOT install
- `install` runs *after* `test` in the lifecycle. If a (even pre-existing/flaky) test fails,
  the build stops and **nothing is installed to `.m2`** — later modules silently resolve the
  previous artifact.
- **Do:** install framework/remix changes with `-DskipTests` (or fix the failing test first),
  and confirm the `.m2` jar timestamp moved.

### 1.4 Prefer reactor builds for cross-module changes
- When a sample depends on a framework module you just edited, build from the repo root with
  `mvn -pl juke-samples/<sample> -am package` so the sample consumes freshly-built reactor
  output instead of a stale `.m2` SNAPSHOT. (Still `clean` the sample — see 1.2.)

### 1.5 Don't trust `cmd | tail` exit codes
- `some-cmd 2>&1 | tail` reports the **pipe's** exit status (tail = 0), masking failures. An
  `npm install` that actually failed looked like it "succeeded" because of the pipe.
- **Do:** check the real exit code, or grep explicitly for `BUILD FAILURE` / `npm error`.

---

## 2. Verify framework behavior — don't assume from docs

### 2.0 Code-reading is a hypothesis, not evidence — run it before you write it down

Reading the source tells you what the code *probably* does; only running it tells you what it
*does*. On this build, several claims were stated as fact from code-reading alone and needed an
asterisk on review. The cost of being wrong here is high: docs that confidently describe behavior
nobody tested are worse than no docs. Discipline:

- For any **behavioral** claim that steers a design decision or lands in docs, write the smallest
  test that exercises it and run it. A working *alternate* path is not proof the path you avoided
  would have failed (e.g. "the client-side timeout worked" does **not** prove a server-side
  executor would have lost the session context — §2.3).
- Rank your evidence: **verified-by-execution > verified-by-reading-source > remembered-from-training.**
  Tag anything below the top tier as *inferred* so future-you knows what's load-bearing-but-unproven.
- Even library facts ("`RestTemplate` is non-final", "CGLIB can't subclass `final`") deserve a
  one-line reflection assert against the *actual* classpath rather than a confident sentence.

**Claims from this build — now verified by tests.** Each was first asserted from code-reading,
then backed by a passing test (full `juke-framework` suite green):

| Claim | Verified by | Result |
|---|---|---|
| Offloading a `@Juke` call to a worker thread loses the session/replay context (§2.3) | `ExecutorSessionContextLossTest` + `JukeFactoryNewInstanceTest#newInstance_sessionContextLookupFails_fallsBackToNormalFlow` | **Confirmed** — request thread gets the session-aware proxy; the worker falls back to global mode (raw bean). |
| Class-level `@Juke` and the concrete-field path ignore the session cookie; only interface `@Juke` is session-aware | `ConcretePathSessionAwarenessTest` | **Confirmed** — under an active session + global `IGNORE` the concrete paths pass through, while the interface path routes to `SessionAwareReplayHandler`. |
| Class-level `@Juke` on a `final` class fails fast with a clear message | `FinalClassProxyTest` | **Confirmed** — `createClassProxy` throws naming "final"/"subclass" (a raw CGLIB exception — clear enough, though not Juke-wrapped). |
| A concrete-typed `@Juke` field wraps as an assignable CGLIB subclass and delegates (the old `@JukeTemplate` failed here at `field.set`; that annotation is now removed) | `JukeConcreteFieldTest` | **Confirmed** — `TemplateRecordingWrapper` wraps a concrete field as an assignable CGLIB subclass (`createClassProxy`); `name`/`excludeMethods` honored; interface field → JDK proxy. |
| Legacy `$`-name recordings still replay under current code | `SessionAwareReplayHandlerTest#activeSession_legacyFullNameFallback_usesLegacyIdentifier` | **Confirmed** — a legacy FQN/`$`-name entry resolves and replays. |

### 2.1 Confirm annotation semantics actually fire in *your* mode
- `@JukeIgnorable` was effectively a **no-op in Community session replay** because the
  session handler compared args with `String.valueOf` and never consulted the annotation.
  (Fixed now, but the lesson stands.)
- `@JukeController` request/response diffs were silently no-ops in **cookie-session replay**:
  the advice gated on `JukeRuntimeHolder.current().mode()`, which session-start never flips
  (only `/service/replay/start` does). Sidecars wrote fine during global RECORD but never got
  compared on session-driven REPLAY runs — a *clean* replay looked just like a *poisoned* one.
  Fixed by also checking `JukeSessionContext.isPlaybackActive()` and reading baselines from the
  per-session DAO.
- Adjacent gotcha when fixing the above: `JukeStorage.asString(id)` auto-appends `.json` while
  `writeDirectEntry(key, content)` takes the full filename. Pass `asString` the identifier
  *without* the `.json` suffix or every lookup silently misses.
- **Do:** write a tiny assertion that the annotation/feature actually changes behavior in the
  exact mode the sample uses (global vs session replay), rather than trusting the README. A
  good shape: an intentionally-poisoned replay call that should produce a `CONTROLLER_MISMATCH`
  line and a corresponding clean call that should not.

### 2.2 The Remix `classAndMethodSequence` format is the *short* name
- Public docs/comments show several forms. The **current** recorder writes short names, so the
  live id is `IOrderManagementSystem.submitOrder.2` — not the FQN/`$`-prefixed legacy form in
  stale code comments.
- **Do:** never hard-code it. Derive it at runtime from `GET /service/recording/inputs?track=…`
  by stripping `.args.json` off the relevant entry's `file`. This is format-proof.

### 2.3 `@Juke` calls must stay on the HTTP request thread  *(verified — `ExecutorSessionContextLossTest`)*
- The replay/session context is request-scoped (`JukeSessionContext` is `@Scope("request")` in
  `JukeConfiguration`). Offloading a `@Juke` call to an `ExecutorService`/`CompletableFuture`
  (e.g. for a server-side timeout) **loses the context**: the worker thread can't see the
  request-scoped session, so `JukeFactory` falls back to the global mode. Confirmed by test — the
  same `newInstance` call yields a session-aware proxy on the request thread and the raw bean on a
  worker thread.
- **Consequence (and what the sample does):** detect "request is taking too long → queued"
  **client-side** (an axios timeout), with the user-facing id generated client-side so it's
  available even when the server response never arrives. A server-side executor-based timeout is
  not an option here — it would break session-scoped replay.

---

## 3. Juke control-flow gotchas

### 3.1 `record/end` used to brick the control surface (now fixed)
- `record/end` reverted the global mode to `NONE`, and `getGlobaljuke()` maps `NONE → null`,
  which every `/service/*` endpoint treats as "Unavailable Service" (HTTP 500) — with **no REST
  way to recover** (record/replay/remix all guard on null). Record→replay in one process was
  impossible without a JVM restart.
- Fixed by making `stop()` revert to `IGNORE` (a real passthrough mode that reports non-null).
- **Do:** if a new control endpoint guards on `getGlobaljuke() == null`, remember `NONE` trips
  it; leave the runtime in `IGNORE` when "idle".

### 3.2 Sessions are the clean path for per-run replay + reports
- Use `@Juke` on the seam: it records under global record mode (no cookie) **and**
  replays per-session when a `JUKE_SESSION` cookie is present (`JukeFactory` checks the session
  *before* the global mode). `@Juke`'s default value is `"juke"` — no need to spell it out.
  One annotation covers both record and replay.
- Each replay run = one `session/start?track=…&description=…` → drive → `session/stop`. The
  `recording/report?track=…` then shows one session per run with `COMPLETED` /
  `COMPLETED_WITH_DEVIATIONS`.

### 3.3 Remix schedules are global and persist across runs
- A scheduled delay/exception stays registered until cleared, so run N's fault bleeds into
  run N+1 (e.g. a delay + an exception both firing on the same entry).
- **Do:** call `GET /service/remix/clear` at the start of each run to reset fault injection.

---

## 4. Playwright authoring patterns that worked

- **One spec, multiple phases** for a multi-run journey, in `test.describe.configure({ mode: 'serial' })`.
  Drive each run on the *same* page load (don't reload) so `window.__coverage__` accumulates;
  snapshot it with a `captureCoverage` helper before any navigation.
- **Sessions via `page.request`**, not the standalone `request` fixture: `page.request` /
  `page.context().request` shares the browser context's cookie jar, so a `session/start` it makes
  sets `JUKE_SESSION` cookies that the SPA's own XHRs then carry.
- **Headless escape hatch:** keep the demo headed + `slowMo` + explicit read-pauses (so a human
  can watch), but read `headless: !!process.env.JUKE_HEADLESS` so CI / quick verification can run
  invisibly.
- **Assert on a durable log, not just transient popups:** a 5s popup is easy to miss; also render
  a persistent on-screen log row per outcome and assert on that. (Wait on the popup's
  `data-status` attribute to confirm the *visual* appeared.)
- **Derive remix targets at runtime** (see 2.2) inside the spec.

---

## 5. Coverage wiring

- `juke-coverage` **auto-configures** — do **not** add `org.juke.coverage` to `@ComponentScan`
  (double registration → startup failure). Scan only `org.juke.framework`, `org.juke.remix`,
  and your app package.
- The seam implementation is auto-excluded from server coverage (`excludedSeams`); design the
  "real" upstream impl as the displaced `@Juke` bean so this shows up naturally.
- **UI coverage exists only after `global-teardown`** runs (it turns harvested
  `window.__coverage__` into the nyc report). During the test the UI half reports
  `available: false`; the report iframe (`/coverage/ui/index.html`) 404s until the run finishes.
  Open the **server** report (`/coverage/server/index.html`) during the test instead.
- **Tune thresholds to what the journey actually exercises.** A server *branch* threshold gated
  on deliberately-uncovered admin methods, leaving the badge red despite good line coverage. Set
  thresholds against the exercised path; gate `branch` only if the happy path has branches.
- Keep a couple of **intentionally-unused methods/branches** (admin ops, an unreached UI panel)
  so coverage is realistically < 100% and the gate is meaningful.

---

## 6. Environment / offline

- npm registry access can fail with `UNABLE_TO_VERIFY_LEAF_SIGNATURE` (cert interception).
  - For the **web app**, the `frontend-maven-plugin` install of node + `npm install` happens on
    the first full build; afterwards you can rebuild the bundle **offline** by invoking Vite
    directly (`node node_modules/vite/bin/vite.js build --mode coverage`) — no `npm install`.
  - For **Playwright**, deps + the Chromium binary may already be installed under another sample;
    reuse via a `node_modules` directory junction rather than re-downloading.
- **Commit lockfiles** and consider committing the recorded track ZIP so the demo is runnable
  without network round-trips.

---

## 7. Demo runner scripts (`.bat` + `.ps1`) — write once, run anywhere

Every sample ships **two flavors** of two scripts so a reviewer can clone and run on bare
Windows with no IDE: `demo-start-server.{bat,ps1}` and `demo-run-curl.{bat,ps1}` (Playwright
samples additionally get `demo-run-playwright.{bat,ps1}`). Below are the cross-shell pitfalls
that ate hours when writing them — keep them straight or you'll relearn them.

### 7.1 PowerShell + curl interop

- **`curl` is an alias for `Invoke-WebRequest` in PowerShell.** Calling `curl -s -o ...` will
  silently invoke IWR with different semantics and break in confusing ways. Bind explicitly at
  the top of the script:
  ```powershell
  $curl = "curl.exe"
  function Invoke-Curl([string]$Url) { return (& $curl -s $Url) }
  ```
- **Use `NUL` (not `$null` or `/dev/null`) when redirecting curl output on Windows** — `curl.exe`
  is a Win32 binary and only understands the Windows null device:
  ```powershell
  & $curl -s -o NUL -w "%{http_code}" "$Base/health"
  ```
- **Don't name a parameter `$args`** — it's an automatic variable in PowerShell. Use
  `param([Parameter(ValueFromRemainingArguments=$true)]$Rest)` or named typed params.
- **JSON POST bodies lose quoting through PowerShell's parser.** `curl -d '{"k":"v"}'` arrives
  at curl as `{k:v}` (quotes stripped). Pipe via stdin instead:
  ```powershell
  '{"k":"v"}' | & $curl -s --data-binary "@-" -H "Content-Type: application/json" $Url
  ```

### 7.2 cmd.exe quirks

- **Chained `if not "x"=="2" if not "x"=="3" (...)` is a parse error.** Use flag variables or
  a single `if ... else`. For a reachability check, sidestep parsing entirely and let curl
  return the exit code:
  ```bat
  curl -s -f -o NUL "%BASE%/health"
  if errorlevel 1 ( echo Server is down & exit /b 1 )
  ```
  This is shorter than parsing `%{http_code}` out of a `for /f` and works identically on every
  Windows since XP.
- **`for /f "delims=" %%r in ('curl -s "<url>"') do set X=%%r`** *does* work for capturing
  output, but URLs with `?` and `=` are fragile — always quote the URL and remember `%` must be
  doubled in scripts (`%%n` in `for`, `%%%%` to emit a literal `%`).
- **`setlocal enabledelayedexpansion` + `!VAR!`** is required if you want to read a variable
  set inside a `for` loop or `if` block. Without it, `%VAR%` expands at parse time and stays
  empty.

### 7.3 Cross-shell parity

- Keep the two flavors **behaviorally identical** — same steps, same step numbers, same
  human-readable banners. A reviewer should be able to pick either by personal preference and
  see the same journey.
- **Invoking `.bat` from bash uses `cmd.exe //c <abs-path>`** (double slash to escape MSYS's
  path mangling). Relative paths get backslashes consumed and silently fail. When you're
  testing the bat from your bash terminal, give the absolute Windows path.

### 7.4 Canonical structure for `demo-start-server.{bat,ps1}`

1. Find JDK 25 — candidates in order: `$JAVA_HOME`, `~/.jdks/ms-25.0.3`,
   `~/.jdks/openjdk-25`, `C:\Program Files\Java\jdk-25`, `C:\Program Files\Eclipse Adoptium\jdk-25`.
   Bail with a useful message + install pointer if none exist.
2. Locate the jar at `%~dp0target\juke-sample-<name>-0.0.1-SNAPSHOT.jar`
   (or `Join-Path $ScriptDir target\...` in PS). Bail with the **exact** build command if missing:
   `mvn -pl juke-samples/juke-sample-<name> -am package -DskipTests`.
3. Ensure recording dir exists: `%USERPROFILE%\juke\sample-<name>\` (matches
   `juke.path` in `application.yml`). Create if missing.
4. Banner with URL + Ctrl+C hint, then `java "-Djuke.enabled=true" -jar "<jar>"`.

Samples that demo replay-only (e.g. `juke-sample-session`) launch differently:
`-Djuke=replay -Djuke.path=<test-resources> -Djuke.zip=<track-name>` — per the spec they're
mirroring. Document this in a header comment so future-you doesn't "fix" it.

### 7.5 Canonical structure for `demo-run-curl.{bat,ps1}`

1. Reachability check (see 7.2) — fail fast with a hint pointing at the matching
   `demo-start-server` script.
2. Each phase prints a magenta/yellow banner with phase number + description, runs its curl
   calls suppressed, then prints the relevant inputs/outputs so a watcher can follow along.
3. Cookie-session demos: separate jars in `$env:TEMP` (or `%TEMP%`),
   e.g. `juke-<sample>-cookies-A.txt` and `-B.txt`. **Delete them at the start of every run**
   so a previous session's cookies don't leak in. Use `curl -s -c <jar>` to *save* and
   `curl -s -b <jar>` to *send*. Don't combine flags in a single call unless you want both.
4. Quote every URL (handles `?`, `&`, `=`). In `.bat`, double the `%` in literal queries like
   `?key=%%n`. In `.ps1`, prefer `"$Base/api/...?track=$Track"` interpolation.

### 7.6 Sample-specific gotchas worth noting in the script header

- **`@JukeController` + `@Juke` field on the same controller**: `JukeBeanPostProcessor` sees a
  null field on the CGLIB proxy (Spring injects `@Autowired` into the target, not the proxy),
  logs WARN, skips wrapping. The cookie filter has nothing to delegate to, so cookie sessions
  **don't engage replay** for that controller. If your sample triggers this (the
  session-greeting controller did), put a KNOWN ISSUE block in the script header and file a
  framework bug rather than chasing a "broken demo".
- **Concrete-field `@Juke` (e.g. `RestTemplate`)** binds its CGLIB subclass at `@PostConstruct`
  and caches it for the JVM lifetime (see CLAUDE.md "Test isolation"). After
  `/service/replay/end` the proxy keeps serving recorded responses positionally — you
  **cannot** cleanly demonstrate "pass-through restored" without a JVM restart. Don't add a
  step that claims to.

### 7.7 Validate end-to-end, not just "the script ran"

Same rule as §2.0 for scripts: *running cleanly* is not *demonstrating the feature*. After
each script lands, run both flavors (bash for the PS, cmd for the bat) against a freshly-built
jar and grep the output for the **specific evidence** the demo is claiming — recorded vs live
quoteId match, distinct per-session `percentComplete`, the registered plugin id, etc. A script
that prints its banners and exits 0 while the underlying behavior didn't fire is the same
class of failure as a green test that didn't actually assert.

---

## 8. Reference implementations must SHOW the feature, not just exercise it

This one is worth its own section because we got it wrong on `juke-sample-todo`. The original
demo recorded + replayed through `@Juke` and confirmed the recorded IDs came back — but the
controller is also `@JukeController`, which **advertises drift detection** (request/response
diff per call), and nothing in the demo surfaced it. A reviewer could run the whole journey
without ever seeing what `@JukeController` actually does.

**The rule.** If a sample's seam carries an annotation, the demo (UI + curl + Playwright) must
show *that annotation's distinguishing behavior* in a way the user can see. "It worked
silently" is not a demo of drift detection. "The server logged it" is not a demo for anyone
who didn't tail the server window.

**How drift surfaces today** (so you don't reinvent the wheel):

| Layer | Where the drift lives | How to surface it |
|---|---|---|
| `@Juke` (interface seam, cookie session) | `JukeSessionEntry.CallRecord.inputMatched` — `recordedArguments` vs `actualArguments` per call | `GET /service/recording/report?track=…` returns per-session calls with `overallStatus: COMPLETED_WITH_DEVIATIONS` + per-call `inputMatched: false`. The UI renders that JSON in a table and highlights deviating rows. |
| `@JukeController` (HTTP boundary, global replay) | Server log only — `JukeControllerAdvice` writes `LOG.info("CONTROLLER_MISMATCH …")` and does not feed the session report | Either client-side diff (see `juke-sample-controller`'s side-by-side panel), or assert on log output via `OutputCaptureExtension`. The advice intentionally never alters the live response. |

The todo sample now uses the first path: a "Session report" panel auto-fetches the report
JSON after the user stops a session, and renders a red-highlighted row when the user typed an
inconsistent title. The same drift shows up in both demo flavors:

- `demo-run-curl.{bat,ps1}` step 4 records a baseline, opens a cookie session, POSTs a
  deliberately-wrong second title, stops the session, and prints the report (PS pretty-prints;
  bat dumps via temp-file + `type` because `for /f` only captures the last line of multi-line
  JSON output).
- `demo-run-playwright.{bat,ps1}` drives the same journey in headed Chrome with `slowMo=1500`
  so a viewer can see each transition (mode pill flip, form fill, report row light up red).

**Do this for every sample you build.** Before declaring done, ask: *if a reviewer ran only
this demo, would they see what the annotation does?* If the answer is "they'd have to read
the server logs to know," the demo isn't done.

### 8.1 UI testid pitfalls when the report has repeating sequences

Per-method sequence counters mean two unrelated rows can both be `sequence=2`
(e.g. `createTodo#2` and `getAllTodos#2`). A `data-testid="call-${sequence}"` collides — `.last()`
picks the wrong row and the assertion fails on a row that has no `diff-bad` class. Use a
disambiguated testid like `data-testid="call-${method}-${sequence}"` so the spec can target
`call-createTodo-2` and assert against the actual drift row.

### 8.2 Inline `onclick="someAsyncFn()"` + Playwright race

Playwright's `page.click()` returns once the event is dispatched — it does **not** await the
inline handler's async work. Two rapid `fill → click → fill → click` pairs can be followed by
a `Stop session` click *before* the second handler's POST completes, leaving only the first
call in the session history (silent test failure: the drift you set up never gets sent).
Defense: after each click, wait for a DOM signal that the handler finished — easiest is an
input field the handler clears after the POST resolves:

```ts
const addAndWait = async (title: string) => {
  await newTitle.fill(title);
  await page.getByTestId('btn-add').click();
  await expect(newTitle).toHaveValue('', { timeout: 5_000 }); // handler cleared the field
  await page.waitForLoadState('networkidle');                  // refresh GET done
};
```

This is the same lesson as §2.0 (verify what you claim) in a different domain: a passing
selector is not a passing journey. Inspect the JSON the server actually saw — if your test
"passes" with the wrong call count, the next person to touch it has no signal that drift
detection is broken.

### 8.3 SlowMo: 0 for verification, ≥1500ms for the headed demo

The same spec should serve double duty: a fast CI verification (`JUKE_HEADLESS=1`, `slowMo=0`,
~5s) and a watchable demo (headed, `slowMo=1500`, with a `page.waitForTimeout(3_000)` at the
end on the drift row so the human sees the result before the browser closes). Read
`process.env.JUKE_HEADLESS` to switch:

```ts
test.use({
  channel: 'chrome',
  headless: !!process.env.JUKE_HEADLESS,
  launchOptions: { slowMo: process.env.JUKE_HEADLESS ? 0 : 1500 },
});
```

`juke-demo.spec.ts` uses `slowMo: 2000` for a guided narrative; 1500 is a good default for
shorter drift-style demos. Anything under ~800 is too quick to follow the transitions.

---

## 9. Pre-flight checklist for the next sample

- [ ] Vite `outDir` → `target/classes/static`; no source-tree `dist/`; no copy step.
- [ ] `@Juke` on the seam (records globally, replays per-session — `"juke"` is the default value, so no argument needed).
- [ ] Seam method declares the checked exception you intend to inject (so Remix can throw it).
- [ ] Non-deterministic fields marked `@JukeIgnorable` **and** a test that proves replay reports
      `COMPLETED` despite them changing.
- [ ] Each replay run: `remix/clear` → `session/start` → drive → `session/stop` → `report`.
- [ ] Remix target derived from `/service/recording/inputs`, never hard-coded.
- [ ] After any framework change: `mvn clean package` the sample, then **grep the embedded
      dependency class** to confirm the change shipped.
- [ ] Coverage thresholds set against the exercised path; `juke-coverage` not in `@ComponentScan`.
- [ ] Playwright: serial phases, `page.request` for sessions, `JUKE_HEADLESS` escape hatch,
      assertions on a durable log.
- [ ] Verify end-to-end by actually running the journey against the freshly-built jar before
      declaring done — config-only changes still warrant a smoke run.
- [ ] Every **behavioral** claim that lands in docs or steers a design choice is backed by a test
      you *ran* (§2.0). Tag anything you only read in source as *inferred*, and don't let an
      inferred claim masquerade as verified.
- [ ] Ship `demo-start-server.{bat,ps1}` and `demo-run-curl.{bat,ps1}` (and `demo-run-playwright.{bat,ps1}`
      where applicable). Both flavors behaviorally identical (§7.3). PS uses `$curl = "curl.exe"`,
      `-o NUL`; bat uses `curl -s -f -o NUL` + `if errorlevel 1` for reachability (§7.1, §7.2).
- [ ] Cookie-session demos: fresh-delete cookie jars at start, separate `-A`/`-B` files in
      `$env:TEMP`/`%TEMP%`, `-c` to save and `-b` to send (§7.5).
- [ ] Document sample-specific traps in the script header (`@JukeController` + cookie session
      bug, concrete-field proxy caching — §7.6).
- [ ] Every annotation on the seam has its distinguishing behavior **visible** in the demo
      (UI panel + curl printout + Playwright assert) — not just exercised, not just logged
      server-side. See §8 for how `@Juke` drift surfaces via the recording-report endpoint.
- [ ] Playwright testids on per-method report rows include the method name (avoid `call-2`
      collisions when multiple methods share `sequence=2` — §8.1).
- [ ] After every inline-async click in the spec, await a DOM signal that the handler
      finished (cleared input, log line, network idle — §8.2).
- [ ] Spec switches `headless` + `slowMo` on `JUKE_HEADLESS` so the same file serves CI
      verification and the headed demo (§8.3).
- [ ] Mirror new scripts to the public repo by committing here, then `python export-community.py
      --commit "msg"` (CLAUDE.md "Community edition").
