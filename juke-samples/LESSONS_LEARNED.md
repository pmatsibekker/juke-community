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
- **Do:** write a tiny assertion that the annotation/feature actually changes behavior in the
  exact mode the sample uses (global vs session replay), rather than trusting the README.

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
- Use `@Juke("juke")` on the seam: it records under global record mode (no cookie) **and**
  replays per-session when a `JUKE_SESSION` cookie is present (`JukeFactory` checks the session
  *before* the global mode). One annotation covers both record and replay.
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

## 7. Pre-flight checklist for the next sample

- [ ] Vite `outDir` → `target/classes/static`; no source-tree `dist/`; no copy step.
- [ ] `@Juke("juke")` on the seam (records globally, replays per-session).
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
