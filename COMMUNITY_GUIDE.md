# Juke Community Guide

> A practical, hands-on tour of everything the open-source Juke agent gives you — annotations, modes, the `/service/*` HTTP contract, recording format, sessions, fault injection, functional test coverage, and a runnable end-to-end example.

This document covers the **Community** distribution: `juke-framework`, `juke-remix-rest-service`, `juke-plugin-api`, `juke-plugin-sdk`, and the optional `juke-coverage` module. All five are Apache 2.0 and ship as plain Spring Boot starter jars — no database, no admin UI, no Enterprise dependencies. The Enterprise admin server and recording aggregation are covered separately in [`ENTERPRISE_GUIDE.md`](ENTERPRISE_GUIDE.md).

---

## Table of contents

1. [What Juke is](#1-what-juke-is)
2. [Five-minute quick start](#2-five-minute-quick-start)
3. [The annotation surface](#3-the-annotation-surface)
4. [Operating modes](#4-operating-modes)
5. [Configuration](#5-configuration)
6. [Storage — folder-backed by default](#6-storage--folder-backed-by-default)
7. [The `/service/*` HTTP contract](#7-the-service-http-contract)
8. [Sessions and per-track replay](#8-sessions-and-per-track-replay)
9. [Remix — runtime fault injection](#9-remix--runtime-fault-injection)
10. [Recording format](#10-recording-format)
11. [Functional test coverage](#11-functional-test-coverage)
12. [Sample application walkthrough](#12-sample-application-walkthrough)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. What Juke is

**What it is.** Juke is a **record-and-replay framework for Spring Boot services**. It sits between your service-layer beans and the upstream collaborators they call — other services, databases, REST endpoints, message brokers, anything reachable through a Spring-injected interface or `*Template`. In **record** mode it captures every upstream interaction as a JSON document inside a portable ZIP archive; in **replay** mode it serves those documents back deterministically, so no upstream call ever leaves the host process. The result is a deterministic test fixture made of *real* data — UI tests, integration tests, and CI pipelines all see byte-identical responses run after run, regardless of what the underlying upstream system is doing.

**Why it's a better way to test.** Unit-testing frameworks (Mockito, EasyMock) and stub / behaviour tools (WireMock, MockServer, Spring Cloud Contract) all make you *author the fake yourself* — you write `when(…).thenReturn(…)`, hand-craft stub JSON, or maintain a separate contract file. That fake is only ever a guess about what the real upstream does, it is boilerplate to write and own, and it drifts silently out of sync the moment the upstream changes. Juke removes the fake entirely:

- **Nothing to author or maintain.** One annotation — `@Juke` — and the test data is captured from the real upstream. No `when/thenReturn`, no stub files, no contract authoring, nothing to keep in sync.
- **Real data, not assumptions.** A recording *is* the upstream's genuine response — its real edge cases, nulls, and encodings included. A hand-written mock only returns what its author imagined; a Juke recording returns what the system actually produced.
- **It exercises your real code.** A Mockito mock replaces the whole bean, so your serialization, retry logic, circuit breakers, and the Spring proxy chain never run under test. Juke replaces *only* the I/O at the upstream edge — every line of your own code on the path still executes. That is what makes a Juke replay a genuine behavioural test rather than a unit-isolated one.
- **Your mental model vs. reality.** A unit test can only be as correct as the author's understanding of the system. If that model has a gap — a field that can be `null`, a status code that only appears in production, an encoding the author never encountered — the mock reflects that same gap and the test stays green while the bug ships. A Juke recording is anchored to behavior a real user actually drove, so it catches exactly the mismatch between the mental model and reality. This is why "green unit tests" are not the same as "correct software".
- **Deterministic and parallel-safe.** Replay is byte-identical on every run, which removes the root cause of flaky tests; and cookie-scoped sessions let many Playwright or integration workers each replay a different recording against a single JVM with no shared state.

Put plainly: Mockito tests your code against *your assumptions* and WireMock tests it against *a stub you maintain* — Juke tests it against *what the real system actually did*, with zero mock code to write or own. Because the recording is anchored to real user behavior rather than an author's mental model, a passing Juke replay is evidence that the feature works correctly, not just that someone's assumptions are internally consistent.

```
┌──────────────────┐       ┌──────────────────────┐       ┌────────────────┐
│  Your tests /    │──────►│  Your Spring Boot    │──────►│  Real upstream │
│  Playwright /    │       │  application         │       │  services      │
│  Cypress /       │       │  (annotated with     │       │  (DBs, APIs,   │
│  curl            │       │   @Juke)             │       │   queues, …)   │
└──────────────────┘       └──────────┬───────────┘       └────────────────┘
                                      │
                                      ▼
                            ┌──────────────────────┐
                            │  juke-framework      │
                            │                      │
                            │  RECORD ─► writes    │
                            │           track.zip  │
                            │  REPLAY ─► reads     │
                            │           track.zip  │
                            │  IGNORE ─► passthru  │
                            └──────────────────────┘
```

The whole library is roughly two ideas: a small set of **annotations** (`@Juke`, `@JukeController`, `@JukeIgnorable`) that mark which beans to intercept, and a **mode flag** (`record` / `replay` / `ignore`) that decides what the interceptor does.

### How Juke fits together (at a glance)

Five concepts cover the whole framework. Each gets its own section below; holding them in mind first makes the rest of this guide easier to navigate.

- **The seam — `@Juke`, `@JukeController`, `@JukeIgnorable`** (§3). The annotations that mark which beans to intercept. `@Juke` is the workhorse — interface fields, concrete fields (e.g. `RestTemplate`), and whole classes; the others handle controller-level advice and per-member opt-outs.
- **The mode — `record` / `replay` / `ignore` / `disable`** (§4). What the interceptor does at the seam. Globally configured, but usually flipped at runtime over REST with no restart.
- **The recording — a ZIP of JSON** (§10). Plain-text, portable, inspectable. Commit it, share it, attach it to a bug report; there is no proprietary format.
- **The session — cookie-scoped per-track replay** (§8). Many test workers can each replay a different recording against one JVM in parallel, with no shared state.
- **Remix — runtime fault injection** (§9). Schedule one-shot exceptions or delays on named calls against a recording you already trust — the cleanest way to test retry, timeout, and error-banner code paths.
- **Functional test coverage — `juke-coverage`** (§11). Accumulates line coverage across every Juke replay session and Playwright journey — server-side via JaCoCo, front-end via Istanbul — with no hand-rolled coverage harness to write.

The HTTP control surface (§7) ties them together: every concept above is driven through `/service/*` endpoints.

---

## 2. Five-minute quick start

Juke is **off by default**. A single YAML key — `juke.enabled: true` — opts an environment in. When the key is missing or false, the framework jar can sit on the classpath without touching your runtime: no proxies wrap your beans, no session beans register, no `/service/*` controllers map, zero overhead. When set, the framework wires its full surface and you drive record/replay from REST and per-session cookies — no JVM restarts, no command-line arguments.

### Prerequisites

- Java 25
- Maven 3.9+
- An existing Spring Boot 3.x application (or any of the bundled samples under `juke-samples/`)

### Add the dependencies

```xml
<dependencies>
    <dependency>
        <groupId>org.juke.harnesss</groupId>
        <artifactId>juke-framework</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>org.juke.harnesss</groupId>
        <artifactId>juke-remix-rest-service</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </dependency>
</dependencies>
```

`juke-framework` carries the engine (annotations, proxies, storage SPI). `juke-remix-rest-service` adds the `/service/*` HTTP control surface — sessions, recordings catalog, plugin registry, and global record/replay endpoints.

### Open the component scan

```java
@SpringBootApplication
@ComponentScan(basePackages = {"org.juke.framework", "org.juke.remix", "com.yourcompany"})
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

### Configure `application.yml`

```yaml
juke:
  enabled: true             # master switch — turn Juke on for this environment
  path: /tmp/juke           # filesystem directory holding recording ZIPs
```

Two settings: the master toggle and a storage location. Per-environment files (`application-prod.yml` typically without `juke.enabled`, `application-dev.yml` with it set to `true`) cleanly separate where Juke runs from where it doesn't.

### Annotate one collaborator

```java
@Service
public class CheckoutService {

    @Juke
    private InventoryClient inventory;        // <-- this dependency will be intercepted

    public Order place(String sku, int qty) {
        return inventory.reserve(sku, qty);
    }
}
```

### Boot the app once

```bash
mvn spring-boot:run
```

With `juke.enabled: true` Juke is wired but **passthrough until told otherwise** — every `@Juke`-wrapped call still goes through the framework's interceptor, but the interceptor defers to the real implementation until a session or REST call switches it to record or replay. Production traffic is unaffected by the existence of test recordings on the filesystem.

### Replay one track for one test (per-session, no restart)

This is the cleanest workflow. Each test session gets its own track via a `JUKE_SESSION` cookie; multiple parallel sessions can each replay a different recording at the same time, and the global Juke mode never has to change. Drop `happy-path.zip` into `/tmp/juke/` and:

```bash
# Start a session — captures a JUKE_SESSION cookie
curl -c /tmp/cookies.txt "http://localhost:8080/service/session/start?track=happy-path"

# Drive the test — every request carrying the cookie replays from happy-path.zip
curl -b /tmp/cookies.txt "http://localhost:8080/checkout?sku=A1"

# End the session
curl -b /tmp/cookies.txt "http://localhost:8080/service/session/stop"
```

Other clients hitting the same backend without that cookie continue to see real upstream responses. Multiple Playwright workers can each open their own session against different tracks without coordinating. See §8 for the full session model.

### Record a new track

Recording is a **process-wide flip** — the framework switches from passthrough to record for every `@Juke` proxy in the JVM while a track is active. With `juke.enabled: true` already set, no additional profile activation is needed. Drive the lifecycle entirely via REST:

```bash
# Start capturing under a track name — flips the global mode to record
curl "http://localhost:8080/service/record/start?track=morning-flow"

# Drive traffic — every @Juke-wrapped call is captured
curl "http://localhost:8080/checkout?sku=A1"
curl "http://localhost:8080/checkout?sku=A2"

# End the track — streams the ZIP back as the response body
curl "http://localhost:8080/service/record/end" -o morning-flow.zip
```

Drop `morning-flow.zip` into `/tmp/juke/` and any session can replay it. The global record mode resets to passthrough as soon as `record/end` returns.

> **A note on the recording asymmetry:** per-session replay works without any global flip — multiple sessions, each on their own track, all running in parallel. Recording today is global: while a track is being captured, every `@Juke`-wrapped call in the JVM enters that track. A clean per-session-record story is on the roadmap so multiple workers could capture different tracks in parallel.

### What about VM arguments and Spring profiles?

`-Djuke.path=...` style VM arguments and `application-record.yml` style operating-mode profiles still work — every `juke.*` YAML key has a system-property equivalent, and you can layer `juke.enabled: true` and `juke.mode: record` into a profile if you prefer. They're a one-to-one alias path for the same settings; **prefer plain YAML in `application.yml` and `application-{env}.yml`**. The toggle pattern above (`juke.enabled` per environment, mode driven by REST) covers every workflow without needing profile gymnastics.

---

## 3. The annotation surface

Juke decides which beans to intercept entirely through annotations — there is no XML, no programmatic registration, and nothing to configure beyond placing the right annotation on the right field, class, or method. Three annotations cover the whole surface, and one of them does almost all the work:

- **`@Juke`** — the workhorse. Apply to a field, class, method, or parameter. Interface-typed fields get a JDK dynamic proxy; concrete-typed fields (e.g. a `RestTemplate`) and class-level usage get a CGLIB subclass. Carries optional `name` / `excludeMethods` for the concrete case.
- **`@JukeController`** — controller-level AOP advice; rarely applied directly.
- **`@JukeIgnorable`** — opts a specific member out of a class-level `@Juke`.

For most applications, `@Juke` is the only one you ever reach for. The subsections below give the full reference for each.

### `@Juke` — interface and class proxies

The workhorse. Apply to any field, method, parameter, constructor parameter, or class.

**On a field — JDK dynamic proxy:**

```java
@Service
public class OrderService {

    @Juke
    private InventoryClient inventory;     // injected interface

    @Juke
    private PricingClient pricing;
}
```

When the field's declared type is an interface, Juke wraps the bean in a JDK dynamic proxy. The proxy implements the interface, intercepts every method call, and routes to the recorder/replayer based on mode.

A field-level `@Juke` on a **concrete** type (e.g. a `RestTemplate`) is wrapped in a CGLIB subclass that delegates to the real bean, so it's assignable back to the field. Use `name` to set the recording-identity prefix (disambiguating two beans of the same type) and `excludeMethods` to skip config/builder methods: `@Juke(name = "shipping", excludeMethods = {"setMessageConverters"})`. A `final` concrete class can't be subclassed — type the field as the interface instead.

**On a class — CGLIB subclass proxy:**

```java
@Juke
@Service
public class FulfillmentService implements Shippable, Trackable {
    public ShipResult ship(Order o) { ... }
    public TrackInfo track(String id) { ... }
}
```

When `@Juke` is at the class level, every public method on the class **and on every interface it implements** is intercepted. Recording entries are keyed by the **concrete class name**, not the interface name — `FulfillmentService.ship.1`, not `Shippable.ship.1`.

`Object.equals` / `hashCode` / `toString` are never intercepted.

**On a method or parameter:**

```java
public class ServiceFactory {

    public IGreetingsService build(@Juke IGreetingsService raw) {
        return raw;       // Juke wraps `raw` before the method executes
    }

    @Juke
    public IGreetingsService getDecoratedGreeting() {
        return real;      // return value is wrapped post-call
    }
}
```

The `value()` attribute optionally pins this annotation to a specific mode regardless of the global setting:

```java
@Juke("ignore")
private DiagnosticsClient diagnostics;     // never recorded — passthrough always
```

Supported values: `juke` (default — follow global mode), `record`, `replay`, `ignore`, `none`, `disable`.

### `@JukeController` — controller-level advice

`@JukeController` (in `juke-framework`'s annotation package) marks a Spring `@RestController` for the controller-level AOP advice — request/response logging in MDC tags, optional finding capture, cookie-bound session resolution. You typically don't need this directly unless you're wiring the controller into the bundle-backed session flow described in §8.

> **Worked example:** `juke-samples/juke-sample-controller`. Its `demo-run-curl.{bat,ps1}` records two calls, then replays one with a poisoned payload: the server logs `CONTROLLER_MISMATCH` without altering the response, so contract drift is visible without breaking the test.

### `@JukeIgnorable` — opt out specific methods or fields

When `@Juke` is on a class, every public method gets wrapped. `@JukeIgnorable` on a method opts that one out:

```java
@Juke
@Service
public class OrderService {
    public Order place(String sku) { ... }   // recorded

    @JukeIgnorable
    public void warmCache() { ... }          // never recorded
}
```

---

## 4. Operating modes

Juke runs in one of five modes at any moment. The mode applies globally to every `@Juke`-annotated bean in the application unless an annotation overrides it locally.

| Mode      | Behaviour                                                                                                                    |
| --------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `juke`    | Default. Follows the active mode set elsewhere (system property, profile, REST call).                                         |
| `record`  | Calls go to the real upstream. Responses + arguments + type metadata are written into the active ZIP.                         |
| `replay`  | Calls are intercepted before reaching the upstream. The recorded JSON is read back, deserialised, and returned to the caller. |
| `ignore`  | Pass-through. Calls hit the real upstream; nothing is recorded. Useful for "Juke is on the classpath but I want this run untouched." |
| `disable` | Master kill-switch. No interception, no recording, no replay. Equivalent to commenting out every `@Juke` in the codebase.     |

### Per-session vs. global

Juke proxies check for an active session cookie **before** they consult the global mode. So the per-session and global mechanisms are layered, not mutually exclusive:

| Caller                                  | Behaviour                                                                                                       |
| --------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| Carries a valid `JUKE_SESSION` cookie   | Replay against the session's track. **Global mode irrelevant.**                                                  |
| No cookie, global mode = `ignore` / `none` | Pass-through to real services.                                                                                  |
| No cookie, global mode = `record`       | Capture the call into the active track.                                                                          |
| No cookie, global mode = `replay`       | Replay against the globally-pinned track.                                                                        |
| Any caller, global mode = `disable`     | Pass-through. The kill-switch wins over everything.                                                              |

This is why the per-session flow in §2 works without flipping global mode: production traffic stays passthrough, your test sessions replay locally.

### Setting the global mode

`juke.enabled: true` makes the global mode reachable; the mode itself can be set three ways, listed in order of precedence (later wins):

1. **REST call at runtime (recommended):** `GET /service/record/start?track=name` flips to record and pins the active track in one step; `GET /service/record/end` flushes the ZIP and returns to passthrough; `GET /service/replay/start?track=name` switches to replay against the named track. No restart, no config edit.
2. **YAML:** `application.yml` → `juke.mode: record` to start in a particular mode rather than passthrough.
3. **System property:** `-Djuke=record` (legacy alias for `juke.mode`; YAML preferred).

Modes can be flipped at runtime via REST without restarting the JVM — see §7.

### How a recording is keyed

Every interception produces an identifier of the form:

```
<TypeName>.<methodName>.<sequence>
```

- `TypeName` is the **interface simple name** for an interface-typed `@Juke` field; the **concrete class simple name** for `@Juke` on a class; and the declared type's simple name (or the explicit `name` attribute) for a concrete-typed `@Juke` field.
- `methodName` is the Java method name. Overloads are disambiguated automatically by a parameter-signature hash.
- `sequence` is a 1-based per-(type, method) call counter incremented on every recorded call.

So three calls to `IGreetingsService.greeting(...)` in record mode produce entries `IGreetingsService.greeting.1`, `IGreetingsService.greeting.2`, `IGreetingsService.greeting.3` in the ZIP. In replay mode, the sequence counter is reset at session start and the same three calls return the same three responses in the same order.

---

## 5. Configuration

Juke needs very little configuration. Every setting lives under a single `juke.*` namespace and ships with a sensible default, so most applications change only one or two keys. This section starts with the keys you actually need, then gives the full reference, and finally — for the rare case where it matters — explains how a setting resolves when more than one source defines it.

### The settings you actually need

For most applications the entire configuration is two keys:

```yaml
juke:
  enabled: true        # master toggle — turn Juke on for this environment
  path: /tmp/juke      # directory where recording ZIPs are written and read
```

`juke.enabled` is the one setting you must decide deliberately — it gates the whole framework (see §4) — and `juke.path` is the one you usually want to pin so recordings land somewhere predictable. Everything else has a default and can be left untouched until a specific need arises.

### Full key reference

Every `juke.*` key, with its default. Beyond the two settings above, these rarely need changing:

| Key                          | Default        | Notes                                                                                                              |
| ---------------------------- | -------------- | ------------------------------------------------------------------------------------------------------------------ |
| `juke.enabled`               | `false`        | **Master toggle.** Required to be `true` for any Juke runtime concept to activate — proxies, session beans, `/service/*` controllers all depend on it. Per-environment YAML opts in: dev/staging set `true`, production omits the key. |
| `juke.path`                  | `./recordings` | Filesystem directory where ZIPs are written and read.                                                              |
| `juke.mode`                  | `ignore`       | Global mode. One of `juke` / `record` / `replay` / `ignore` / `disable`. Usually flipped at runtime via REST rather than set in YAML. |
| `juke.zip`                   | (none)         | Default ZIP name (without `.zip`) for global record/replay. A session can override this per call.                  |
| `juke.tests`                 | (none)         | Optional whitelist of allowed recording names. When set, `record/start?track=foo` rejects anything outside the list. |
| `juke.disabled`              | `false`        | Soft kill-switch (same effect as `juke.mode=disable`). `juke.enabled=false` is preferred — that one prevents proxies from being wrapped at all. |
| `juke.storage.folder.path`   | `recordings`   | Folder root for the `JukeStorage` SPI used by the `/service/recordings` catalog. Defaults relative to the host's working directory. |
| `juke.args-validation`       | `warn`         | `warn` / `strict` / `off`. On replay, compares incoming args against the recorded args; logs a warning, throws, or skips. |
| `juke.coverage.enabled`      | `false`        | **Opt-in gate** for the optional `juke-coverage` module. When absent or `false`, no coverage beans are created. See §11. |
| `juke.coverage.classes`      | (none)         | Absolute path to the application's compiled classes (`target/classes`). Required for server coverage. |
| `juke.coverage.sources`      | (none)         | Absolute path to `src/main/java`. Optional — enables source-line highlighting in the HTML report. |
| `juke.coverage.report-dir`   | `${user.home}/juke-demo/coverage/server` | Directory the server-side JaCoCo HTML report is written into and served from. |
| `juke.coverage.ui-report-dir`| `${user.home}/juke-demo/coverage/ui` | Directory the Playwright/nyc UI report and `coverage-summary.json` are read from. |
| `juke.coverage.bundle-name`  | `Application under test` | Display name for the JaCoCo coverage bundle in the HTML report. |
| `juke.coverage.threshold.server.line` | `0` | Minimum server line coverage (%). `0` = no gate. |
| `juke.coverage.threshold.server.branch` | `0` | Minimum server branch coverage (%). `0` = no gate. |
| `juke.coverage.threshold.server.instruction` | `0` | Minimum server instruction coverage (%). `0` = no gate. |
| `juke.coverage.threshold.ui.lines` | `0` | Minimum UI line coverage (%). `0` = no gate. |
| `juke.coverage.threshold.ui.statements` | `0` | Minimum UI statement coverage (%). `0` = no gate. |
| `juke.coverage.threshold.ui.functions` | `0` | Minimum UI function coverage (%). `0` = no gate. |
| `juke.coverage.threshold.ui.branches` | `0` | Minimum UI branch coverage (%). `0` = no gate. |

### Per-environment toggle pattern

The cleanest production pattern is to gate `juke.enabled` per environment:

```yaml
# application.yml — base config, no enabled key (i.e. defaults to off)
juke:
  path: /tmp/juke-tracks

# application-dev.yml
juke:
  enabled: true

# application-staging.yml
juke:
  enabled: true

# application-prod.yml — omit juke.enabled entirely; production is Juke-free.
```

Activate the environment profile via `SPRING_PROFILES_ACTIVE=dev` (or `prod`, etc.); Juke turns on or stays invisible without code changes.

### How a setting resolves

Most of the time a key is defined once and that is the end of it. When the same key is defined in more than one place, Juke reads it from layered sources, with later overlays winning:

```
juke-defaults.yml            (shipped inside juke-framework.jar)
   ↓
application.yml              (your app's base config)
   ↓
application-{profile}.yml    (profile overlays — e.g. application-record.yml)
```

Every `juke.*` YAML key also has a one-to-one `-Djuke.*` system-property alias, which overrides the YAML value — convenient for one-off CI overrides, though YAML is the recommended path otherwise because it is environment-aware and survives across shells. The translation is mechanical:

| YAML                       | System property               |
| -------------------------- | ----------------------------- |
| `juke.enabled: true`       | `-Djuke.enabled=true`         |
| `juke.path: /tmp`          | `-Djuke.path=/tmp`            |
| `juke.mode: record`        | `-Djuke=record`               |
| `juke.zip: foo`            | `-Djuke.zip=foo`              |

---

## 6. Storage — folder-backed by default

By default, Juke stores every recording as a `.zip` file in the directory named by `juke.path`. That is the whole story for most users: drop a ZIP in, it appears in `/service/recordings`; record a new track, it appears as a new ZIP. No database, no schema, no daemon — just files.

The behaviour is provided by a small SPI you can replace if you need to, which the rest of this section describes.

### The `JukeStorage` SPI

`JukeStorage` is the framework's storage abstraction. It exposes two levels:

- **Per-recording.** Read and write entries within a single ZIP — the methods used during a record/replay session (`readFromFile`, `writeToFile`, `getFileNames`, `path`, …).
- **Recording-store.** Enumerate, load, and save whole recordings under their names — used by the `/service/recordings` endpoints (`listRecordings`, `loadRecording`, `saveRecording`).

The default implementation is `JukeZipDAOImpl`, which treats `juke.storage.folder.path` as the recording store and each `*.zip` file as a per-recording handle. Spring autoconfig (`JukeStorageAutoConfiguration`) registers it as `@Bean @ConditionalOnMissingBean(JukeStorage.class)`, so a host can swap in its own implementation simply by exposing a different `JukeStorage` bean.

### Backend alternatives

For Enterprise customers who want recordings in a database instead of on the filesystem, `juke-scenario-service` ships a `JukeJpaStorageBackend` that activates with `juke.storage.backend=db`. That is covered in [`ENTERPRISE_GUIDE.md`](ENTERPRISE_GUIDE.md) — the agent code is unchanged either way.

---

## 7. The `/service/*` HTTP contract

The `juke-remix-rest-service` module exposes Juke's full control surface under `/service/*`. Recording, replay, sessions, plugin registration, fault injection — everything in the framework is driven through these endpoints, with no JVM restarts and no command-line arguments.

In practice you reach for four endpoints again and again — recording a track and starting a per-session replay are the working core:

- `GET /service/record/start?track={name}` and `GET /service/record/end` — begin and finish a recording.
- `GET /service/session/start?track={name}` and `GET /service/session/stop` — start and end a cookie-scoped per-track replay.

Everything else is reference. The subsections below catalogue every endpoint, grouped by what it controls. All endpoints accept GET unless noted; responses are `application/json` unless an attachment is being streamed.

### Recording control

| Endpoint                                  | Description                                                                                                                                |
| ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `GET /service/record/start?track={name}`  | Begin recording into the named track. The current ZIP is created (or appended to) under `juke.path`.                                        |
| `GET /service/record/end`                 | Flush the current track to disk, close the ZIP, and stream the bytes back as the response body. The download includes a `Content-Disposition: attachment` header so browsers offer a "Save as…" prompt. |

```bash
curl -X GET "http://localhost:8080/service/record/start?track=morning-flow"
# proceeds with normal interactions...
curl -X GET "http://localhost:8080/service/record/end" -o morning-flow.zip
```

### Replay control

| Endpoint                                  | Description                                                                                              |
| ----------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| `GET /service/replay/start?track={name}`  | Switch to replay mode pinned at the named track. The ZIP must already exist under `juke.path`.            |
| `GET /service/replay/disable`             | Temporarily pass through to real services without leaving replay mode.                                    |
| `GET /service/replay/enable`              | Re-enable replay after a `disable`.                                                                      |

### Recordings catalog

| Endpoint                                | Description                                                                                                              |
| --------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| `GET /service/recordings`               | Returns a JSON array of every recording name the agent currently holds. Backed by `JukeStorage.listRecordings()`.        |
| `GET /service/recordings/{name}`        | Streams the named recording's full ZIP bytes (`application/zip`) with `Content-Disposition: attachment`. 404 when unknown. |

The Enterprise admin server consumes these to aggregate recordings across multiple agents — see the Enterprise guide.

```bash
curl http://localhost:8080/service/recordings
# ["happy-path","cancel-flow","retry-after-timeout"]

curl http://localhost:8080/service/recordings/happy-path -o happy-path.zip
```

### Sessions (cookie-bound per-track replay)

| Endpoint                                       | Description                                                                                                            |
| ---------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `GET /service/session/start?track={name}`      | Start a per-session replay scoped to the named track. Sets a `JUKE_SESSION` cookie that the framework's cookie filter resolves on every subsequent HTTP request. |
| `GET /service/session/stop`                    | End the active session. Cleans up its temp file and clears the cookie.                                                 |
| `GET /service/session/status`                  | Return the active session's id, track name, age, and step counter.                                                     |
| `GET /service/sessions`                        | Aggregate read-only view of every active session — useful for dashboards.                                              |

Sessions let multiple browser tabs (or test runners) replay different tracks against the same backend simultaneously. See §8.

### Plugin registration relay

`juke-remix-rest-service` carries a thin in-memory plugin registry — community-licensable, no DB. Plugins POST themselves at startup, send heartbeats, and become discoverable through these endpoints. The full plugin lifecycle and how to write a plugin against `juke-plugin-sdk` are covered in the Enterprise guide.

> **Worked example:** `juke-samples/juke-sample-plugin`. A `@PluginCapability(RECORDING_TRANSFORMER)` bean self-registers on startup over `/service/plugins/register`; `demo-run-curl.{bat,ps1}` then lists it via `GET /service/plugins`.

| Endpoint                                              | Description                                                              |
| ----------------------------------------------------- | ------------------------------------------------------------------------ |
| `POST /service/plugins/register`                      | Register a plugin (request body = `PluginRegistration` JSON).             |
| `POST /service/plugins/{pluginId}/heartbeat`          | Liveness ping; rotates the plugin token when sent before the staleness window. |
| `POST /service/plugins/{pluginId}/deregister`         | Voluntary teardown — the registry forgets the plugin.                    |
| `GET  /service/plugins`                               | List every registered plugin's metadata.                                  |
| `GET  /service/plugins/capabilities`                  | List the union of capabilities advertised by current plugins.            |
| `GET  /service/plugins/{pluginId}`                    | Detail for one plugin.                                                   |
| `GET  /service/plugins/{pluginId}/call-log`           | Recent dispatcher calls into this plugin (debug aid).                    |
| `POST /service/plugins/{pluginId}/configure`          | Push capability-specific configuration to a registered plugin.           |

### Remix — runtime fault injection

| Endpoint                                                                                                | Description                                                                                       |
| ------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| `GET /service/remix/exceptionSchedule?classAndMethodSequence={id}&exception={type}&exceptionMessage={msg}` | Schedule an exception to be thrown the next time the named call is replayed.                       |
| `GET /service/remix/delaySchedule?classAndMethodSequence={id}&waitTimeInMS={ms}`                        | Schedule a delay before the named call returns. Useful for testing timeout/retry handling.        |
| `GET /service/remix/clear`                                                                              | Clear all scheduled delays and exceptions. Call it between replay runs so one run's injected fault doesn't carry into the next. |

`classAndMethodSequence` is the recording identifier from §3 (e.g. `IGreetingsService.greeting.1`). The `juke-samples/juke-sample-exceptions` demo is the worked example: it records a SKU order, then replays it with an injected delay (a "queued" UI state) and an injected exception (a "technical difficulties" state), clearing schedules between runs.

### Coverage

Available only when `juke.coverage.enabled: true`. See §11 for the full setup walk-through, prerequisites, and configuration keys.

| Endpoint | Description |
| -------- | ----------- |
| `GET /service/coverage`        | Combined summary — both server and UI in one response, each under its own key with its own `available` flag. Useful for dashboards and CI one-call checks. |
| `GET /service/coverage/server` | Server-side (JaCoCo) coverage summary only. The drill-down HTML report is served at `/coverage/server/index.html`. Always returns `200`; `available: false` when the JaCoCo agent is not attached. |
| `GET /service/coverage/ui`     | Front-end (nyc/Istanbul) coverage summary only. The drill-down HTML report is served at `/coverage/ui/index.html`. Always returns `200`; `available: false` when no Playwright coverage run has been completed yet. |

---

## 8. Sessions and per-track replay

By default, `record/start` and `replay/start` flip a process-wide flag. That works for one consumer at a time. The session API gives every test or browser tab its own track without forcing a process restart.

### Why bother

Imagine three Playwright test workers running in parallel against the same backend. Each worker needs a different track:

- Worker 1 → `track=happy-path`
- Worker 2 → `track=cancel-flow`
- Worker 3 → `track=retry-after-timeout`

Without sessions, you'd have to `replay/start` between every worker's request — a serialisation point that defeats the parallelism. With sessions, each worker hits `/service/session/start?track=...` once at suite setup and gets a `JUKE_SESSION` cookie. Subsequent requests carry that cookie; the framework's cookie filter matches it to the right track per request.

### How to use sessions

```bash
# Worker 1 setup — captures the cookie
curl -c /tmp/cookies-1.txt "http://localhost:8080/service/session/start?track=happy-path"

# Worker 1 makes a request — the cookie carries the track binding
curl -b /tmp/cookies-1.txt "http://localhost:8080/checkout?sku=A1"
```

In a Playwright test, you'd simply set `JUKE_SESSION` in the page context before navigating. Each browser context maps 1:1 to a Juke session.

### Status visibility

`GET /service/sessions` returns one row per active replay session with two fields a status grid actually wants — `lastCall`, naming the recording JSON the session most recently handed back, and `percentComplete`, a coarse 0–100 progress signal:

```bash
curl http://localhost:8080/service/sessions
```

```json
{
  "generatedAt": "2026-05-08T12:00:42Z",
  "activeSessionCount": 2,
  "sessions": [
    {
      "sessionId": "abc-...",
      "track": "happy-path",
      "mode": "replay",
      "startTime": "2026-05-08T12:00:00Z",
      "playTime": "PT42S",
      "summary": { "totalSteps": 5, "completedSteps": 2, "inProgressSteps": 1, "notStartedSteps": 2 },
      "lastCall": {
        "entry":       "com.example.IGreetingsService.$greeting",
        "displayName": "IGreetingsService.greeting.7",
        "sequence":    7,
        "at":          "2026-05-08T12:00:41Z"
      },
      "percentComplete": 47.83,
      "steps": [ /* per-entry currentIndex / totalLength rows */ ]
    },
    {
      "sessionId": "def-...",
      "track": "cancel-flow",
      "mode": "replay",
      "startTime": "2026-05-08T12:00:01Z",
      "playTime": "PT41S",
      "summary": { "totalSteps": 3, "completedSteps": 0, "inProgressSteps": 0, "notStartedSteps": 3 },
      "lastCall": null,
      "percentComplete": 0.0,
      "steps": [ /* ... */ ]
    }
  ]
}
```

A few things worth knowing:

- **`lastCall.displayName`** is rendered as `<SimpleType>.<method>.<sequence>` — `IGreetingsService.greeting.7` instead of the raw ZIP-entry path. The fully-qualified `lastCall.entry` matches `steps[].entry` for cross-referencing.
- **`lastCall` is `null`** until the session resolves its first call. A freshly opened session that nothing has driven traffic against shows `null`/`0.0` rather than fake activity.
- **`percentComplete`** is `sum(currentIndex) / sum(totalLength) * 100` across every entry in the recording — entries the session hasn't touched contribute 0, fully consumed entries contribute their full length. It's a coarse signal, not call-accurate (the underlying `currentIndex` overshoots consumed-call count by up to one step), but it's good enough to drive a progress bar and dramatically more useful than a bare step count.

> **Worked example:** `juke-samples/juke-sample-status-grid`. Its `demo-run-curl.{bat,ps1}` opens two cookie jars against one track; the live grid UI at `http://localhost:8080` polls `/service/sessions` and shows each session's `lastCall` and `percentComplete` advancing.

> **Drift surfacing on the same endpoint:** `juke-samples/juke-sample-todo` exercises `GET /service/recording/report?track=…` — change one input mid-session and the UI report panel renders the per-call table with `recordedArguments` vs `actualArguments` and an `overallStatus` of `COMPLETED_WITH_DEVIATIONS`. `demo-run-playwright.{bat,ps1}` walks the journey visibly.

---

## 9. Remix — runtime fault injection

### Why this exists

The hardest scenarios to test are the ones where upstream behaviour is **wrong but in specific, controlled ways**: an inventory service that takes 30 seconds to respond once and then recovers; a payment gateway that throws a transient `IOException` on the second call but succeeds on retry; a recommendation engine that returns a 503 only when the cart has more than five items.

In **unit tests**, these are trivial — you reach into the mock and tell it to throw or sleep. But unit tests don't exercise the real proxy chain, the real retry policies, the real circuit breakers, or the UI's loading-state handling.

In **behavioural / integration / Playwright tests** they're nearly impossible:

- You can't easily make a real upstream service slow on demand. Network throttling tools work at the OS layer and affect every test on the box.
- You can't make a real upstream throw a specific exception class with a specific message. Killing the process gets you a connection refused, not the `IOException("checksum mismatch")` your retry logic actually filters on.
- Chaos-engineering tools (Toxiproxy, Pumba) work but are heavyweight to set up per test and pollute CI.
- Hand-rolled WireMock setups require maintaining a parallel mock-server config per test scenario.

The result is that test suites tend to **only validate the happy path**. Retry logic, circuit breakers, timeout fallbacks, "service unavailable" UI banners, exponential backoff — all the code that handles real-world upstream misbehaviour — gets shipped without ever being exercised by a behavioural test.

Remix solves this by injecting precisely the failure you want, on precisely the call you want, against a recording you already trust. Your Playwright test stays a clean black-box scenario; the upstream "misbehaves" exactly once, in exactly the right place, then recovers.

### Inject an exception

Throw a synthetic `IOException` the next time `IGreetingsService.greeting.1` is replayed:

```bash
curl "http://localhost:8080/service/remix/exceptionSchedule?\
classAndMethodSequence=IGreetingsService.greeting.1&\
exception=IOException&\
exceptionMessage=Simulated+upstream+failure"
```

The next call to that recording entry throws an `IOException` instead of returning the recorded payload. The call after that returns the recorded payload normally — schedules are one-shot unless re-armed.

**What this lets you test that you otherwise couldn't:**

- Exception-flow stability: does your retry layer catch the right exception class, or does the exception bubble up and crash the request?
- Error-banner UX: does the UI show "We're having trouble — try again" when the second call fails, or does it display the user's name as `null`?
- Logging completeness: is the failure surfaced in observability with the right MDC tags, or does it disappear into stdout?
- Idempotency: if the failed call had a side effect (POST), does the retry produce duplicate orders or correctly recognise the same idempotency key?

### Inject a delay

Add 10 seconds of latency before `OrderService.place.3` returns:

```bash
curl "http://localhost:8080/service/remix/delaySchedule?\
classAndMethodSequence=OrderService.place.3&\
waitTimeInMS=10000"
```

**What this lets you test:**

- **Async UI handling:** does the loading spinner appear, stick around, and disappear correctly when an upstream call takes longer than typical? Many UIs show a flicker on fast responses but never get tested for the slow-path because real upstream services are usually fast enough that the spinner never paints.
- **Timeout fallbacks:** does your `RestTemplate` / `WebClient` actually have a configured timeout? A delay longer than the configured timeout exercises the timeout branch — code that's almost never hit in production until something does go wrong.
- **User-perceived latency:** does the page render the available data while the slow call resolves, or does it block on the whole page?
- **Concurrent request handling:** if Call A is delayed, does Call B (parallel, independent) still complete on time?

### Practical pattern: testing retry-on-transient-failure end-to-end

A test that wants to validate "the UI gracefully retries and recovers from a single upstream failure" typically:

1. Loads a happy-path recording: `replay/start?track=happy-path`.
2. Schedules a one-shot exception on the first call: `exceptionSchedule?...sequence=Service.call.1&exception=IOException`.
3. Drives the UI action — the first call fails as scheduled, the retry hits `Service.call.2` and gets the recorded happy-path response.
4. Asserts the user sees a brief "retrying…" indicator followed by the successful result, and that exactly one error was logged.

No mock objects, no proxy setup, no service-virtualization tooling, no real network failure. The test is deterministic, runs in the same single-process context as everything else, and exercises real production retry code instead of a mocked path.

The same pattern works for testing slow-response handling (delay → assert the spinner) and combined scenarios (delay then exception → assert the timeout fallback path).

---

## 10. Recording format

A Juke recording is deliberately boring: a plain ZIP archive of JSON files, ~kilobytes per call, with no proprietary format and no external dependency that travels with the file. Anything that reads ZIP can open one, and anything that reads JSON can inspect its contents. That is what makes recordings commit-able to a repo, attach-able to bug reports, and trivially diff-able between runs.

Inside the ZIP is one trio of JSON files per recorded call, plus a small metadata catalog:

```
happy-path.zip
├── IGreetingsService.greeting.1.json          ← response body for first call
├── IGreetingsService.greeting.1.args.json     ← input args for first call (sidecar)
├── IGreetingsService.greeting.1.type.json     ← runtime return-type for first call (sidecar)
├── IGreetingsService.greeting.2.json
├── IGreetingsService.greeting.2.args.json
├── IGreetingsService.greeting.2.type.json
├── OrderService.place.1.json
├── OrderService.place.1.args.json
├── OrderService.place.1.type.json
├── juke.json                                  ← class metadata catalog (per-class methods + types)
└── juke-mappings.json                         ← short-name → fully-qualified-name lookup
```

### Why three files per call

- `*.json` — the **response** Jackson serialised. Replay reads this file and returns it as the call's return value.
- `*.args.json` — a sidecar capturing the **input arguments** at record time. On replay, `juke.args-validation` (default `warn`) compares the incoming args against this; `strict` mode throws a `JukeInputMismatchException` so you spot drift early.
- `*.type.json` — the **runtime concrete return type** when it differs from the declared interface return type (e.g. `RestTemplate.getForEntity` declares `ResponseEntity<T>` but the runtime instance is a generic concrete type). Replay uses this to deserialise correctly.

---

## 11. Functional test coverage

Unit-test coverage answers "did my unit tests touch this line?" Juke can answer a different, often more valuable question: "do my real user-journey tests exercise this code?" The optional `juke-coverage` module bridges the two: it accumulates coverage across every replay session the JVM has served, so you see not how many lines your test files import but how many lines your actual user journeys reach.

The two things you need to turn it on:

```xml
<dependency>
    <groupId>org.juke.harnesss</groupId>
    <artifactId>juke-coverage</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

```yaml
juke:
  coverage:
    enabled: true
    classes: /absolute/path/to/app/target/classes   # required for server coverage
```

Both coverage endpoints (§7) are only wired when `juke.coverage.enabled=true`. When the property is absent — the default — no coverage beans are created and the feature has zero runtime overhead.

### Server coverage (JaCoCo)

Server coverage captures which lines of the **server-side application** were executed during Juke replay sessions. It works by reading the JaCoCo agent *in-process* — the agent accumulates coverage across the JVM's full lifetime, so every replay session adds to the same figure automatically. No separate coverage build, no `mvn test`, no extra network hop.

**Prerequisite:** start the server JVM with the JaCoCo runtime agent attached:

```bash
java -javaagent:/path/to/jacoco-agent.jar=output=none \
     -jar your-application.jar
```

The `output=none` flag tells JaCoCo not to write a file on exit — Juke reads the data in-process via `RT.getAgent()` instead.

**Generate a report:**

```bash
curl http://localhost:8080/service/coverage/server
```

```json
{
  "available": true,
  "message": "Coverage generated from live JaCoCo agent data.",
  "tool": "JaCoCo",
  "instruction": 84.2,
  "branch": 71.0,
  "line": 86.5,
  "analyzedClasses": 14,
  "excludedSeams": [
    "com.example.IGreetingsService -> com.example.GreetingServiceImpl"
  ],
  "reportUrl": "/coverage/server/index.html",
  "generatedAt": "2026-05-20T10:00:00Z"
}
```

The drill-down HTML report is immediately available at `/coverage/server/index.html`. When the JaCoCo agent is not attached, `available` is `false` and `message` explains why — the endpoint never errors.

**The `@Juke` seam exclusion.** In replay mode, the implementation behind a `@Juke` proxy never executes — counting it would unfairly depress the result. Juke records every displaced implementation in `JukeMockRegistry` and the analyser skips exactly those classes. The `excludedSeams` array in the response lists what was skipped, so the figure is transparent.

### UI coverage (Istanbul / nyc)

UI coverage captures which lines of the **React SPA** were executed during a Playwright run. Because browser-side execution happens outside the JVM, the pipeline is split: the SPA is built with instrumentation enabled (via `vite-plugin-istanbul`), Playwright harvests `window.__coverage__` at the end of each spec, and `nyc` renders the HTML report and a `coverage-summary.json` into the UI report directory. This endpoint reads that summary.

**Prerequisite:** build the SPA with the coverage Maven profile:

```bash
mvn package -Pcoverage -pl juke-samples/juke-sample-coverage -am
```

Then run the Playwright coverage spec; the fixture automatically harvests and aggregates `window.__coverage__` into `juke.coverage.ui-report-dir`. The dedicated demo at `juke-samples/juke-sample-coverage/` bundles `demo-start-server.{ps1,bat}` and `demo-run-playwright.{ps1,bat}` launchers that wire all of the above up for you — see §12.

**Read the latest summary:**

```bash
curl http://localhost:8080/service/coverage/ui
```

```json
{
  "available": true,
  "message": "UI coverage read from the latest nyc/Istanbul summary.",
  "tool": "nyc/Istanbul",
  "lines": 78.0,
  "statements": 77.4,
  "functions": 80.0,
  "branches": 62.5,
  "reportUrl": "/coverage/ui/index.html",
  "generatedAt": "2026-05-20T10:00:00Z"
}
```

The drill-down HTML report is available at `/coverage/ui/index.html`. Until the first Playwright coverage run completes, `available` is `false` — the endpoint never errors.

### Configuration keys

| Key | Default | Notes |
| --- | ------- | ----- |
| `juke.coverage.enabled`       | `false` | Opt-in gate — all coverage beans depend on this. |
| `juke.coverage.classes`       | (none)  | Absolute path to the application's `target/classes`. Required for server coverage. |
| `juke.coverage.sources`       | (none)  | Absolute path to `src/main/java`. Optional — enables source-line highlighting in the HTML report. |
| `juke.coverage.report-dir`    | `${user.home}/juke-demo/coverage/server` | Directory the server HTML report is written into and served from. |
| `juke.coverage.ui-report-dir` | `${user.home}/juke-demo/coverage/ui`     | Directory the Playwright run writes the nyc report and `coverage-summary.json` into. |
| `juke.coverage.bundle-name`   | `Application under test` | Display name for the JaCoCo coverage bundle in the HTML report. |
| `juke.coverage.threshold.server.line` | `0` | Minimum server line coverage (%). `0` = no gate. |
| `juke.coverage.threshold.server.branch` | `0` | Minimum server branch coverage (%). `0` = no gate. |
| `juke.coverage.threshold.server.instruction` | `0` | Minimum server instruction coverage (%). `0` = no gate. |
| `juke.coverage.threshold.ui.lines` | `0` | Minimum UI line coverage (%). `0` = no gate. |
| `juke.coverage.threshold.ui.statements` | `0` | Minimum UI statement coverage (%). `0` = no gate. |
| `juke.coverage.threshold.ui.functions` | `0` | Minimum UI function coverage (%). `0` = no gate. |
| `juke.coverage.threshold.ui.branches` | `0` | Minimum UI branch coverage (%). `0` = no gate. |

When any threshold is configured, every response carries a `passed` boolean — `true` when all configured metrics are met. A CI script can gate a build without parsing percentages:

```bash
curl -sf http://localhost:8080/service/coverage/server | jq -e '.passed'
```

When a threshold is missed, `message` in the response body describes exactly which metrics fell short and by how much.

Keep `juke.coverage.enabled` absent from production YAML. The feature then has zero runtime footprint even with `juke-coverage` on the classpath, and the `-javaagent` flag belongs only in local and UAT launch scripts.

---

## 12. Sample application walkthrough

The `juke-samples/` directory ships ten runnable Spring Boot references — one for each pattern in this guide. This section walks through `juke-sample-greeting` end-to-end (it's the canonical app and the basis for `juke-samples/DEMO.md`), then catalogs the rest with one journey per sample.

### Running any sample

Every sample directory carries the same three launcher pairs (Windows `.bat` + PowerShell `.ps1`):

| Script | Purpose |
|---|---|
| `demo-start-server.{bat,ps1}` | Locates JDK 25, builds the jar if needed, sets the right `juke.*` / `-javaagent` flags, and boots on port 8080. Run first; leave it running. |
| `demo-run-curl.{bat,ps1}` | Drives the sample's full record → replay → assert journey through `curl`. Run from any second terminal. |
| `demo-run-playwright.{bat,ps1}` (UI samples) | Opens a visible Chrome window via Playwright and walks the same journey with `slowMo` set high enough to follow. `JUKE_HEADLESS=1` runs it as a CI smoke check. |

The launchers encode the journey end-to-end — no profile switches or environment variables to remember. Read the scripts when you want to know exactly what each demo does.

### Sample catalog

| Sample | What it shows | Read first |
|---|---|---|
| [`juke-sample-greeting`](juke-samples/juke-sample-greeting) | Canonical app: one REST endpoint, one interface-typed `@Juke` seam, bundled React SPA. | §2, §3 |
| [`juke-sample-annotations`](juke-samples/juke-sample-annotations) | `@Juke` on fields, methods, constructor parameters; multi-service composition with no controllers. | §3 |
| [`juke-sample-rest-client`](juke-samples/juke-sample-rest-client) | Concrete-field `@Juke` on a `RestTemplate` (CGLIB) with `name` disambiguation + `excludeMethods` for builder methods. | §3 |
| [`juke-sample-controller`](juke-samples/juke-sample-controller) | `@JukeController` capture + contract-drift detection. The demo curl posts a poisoned payload on replay; the server log emits `CONTROLLER_MISMATCH` without altering the response. | §3 |
| [`juke-sample-todo`](juke-samples/juke-sample-todo) | REST CRUD + `@JukeIgnorable` on auto-generated `id`, **and** the visible session-drift UI: change one input during a cookie-replay session and the report panel highlights the drift row. | §3, §8 |
| [`juke-sample-session`](juke-samples/juke-sample-session) | Per-session cookie replay with parallel Playwright workers — each worker carries its own track. Also hosts the cross-sample Playwright suite. | §8 |
| [`juke-sample-status-grid`](juke-samples/juke-sample-status-grid) | Cross-session live grid: many cookie sessions on one track; the UI polls `/service/sessions` and shows each session's `lastCall` + `percentComplete`. | §8 |
| [`juke-sample-exceptions`](juke-samples/juke-sample-exceptions) | Exception / latency flows. A SKU order is driven four times — record, replay, replay+delay ("queued"), replay+exception ("technical difficulties"). | §9 |
| [`juke-sample-coverage`](juke-samples/juke-sample-coverage) | End-to-end functional coverage. Live dashboard polls `/service/coverage`; the Playwright launcher fills in the UI half. | §11 |
| [`juke-sample-plugin`](juke-samples/juke-sample-plugin) | Plugin SDK self-registration. A `@PluginCapability(RECORDING_TRANSFORMER)` bean POSTs itself at startup; `GET /service/plugins` lists it. | §7 (registration relay) |

### Worked example: `juke-sample-greeting`

`juke-samples/juke-sample-greeting` is a complete reference app — a single REST endpoint (`GET /greeting`) that delegates through a Juke-wrapped service interface to an upstream that builds the greeting payload. A React UI is bundled into the same jar via `frontend-maven-plugin`, so launching the jar serves both the API and the SPA from one JVM.

### What's in it

```
juke-samples/juke-sample-greeting/src/main/java/com/example/greeting/
├── GreetingApplication.java      ← @SpringBootApplication; canonical Juke wiring
├── JukeGreetingController.java   ← REST controller @ /greeting
├── JukeGreetingsDAO.java         ← Service bean with @Juke-wrapped collaborator
├── IGreetingsService.java        ← The intercepted interface
├── GreetingServiceImpl.java      ← The "real" upstream impl
└── Greeting.java                 ← Value object (records into JSON)

juke-samples/juke-sample-greeting/src/main/web-app/
└── …                             ← React UI; built and bundled into the jar
```

`GreetingApplication` is a stock `@SpringBootApplication` with an open component scan over `org.juke.framework`, `org.juke.remix`, and `com.example.greeting` — no `excludeFilters`, no JPA. It's the smallest possible Juke host.

### Run it

The sample's `application.yml` already pins `juke.path` and sets `juke.enabled: true` — boot it once and drive recording / replay through the REST API:

```bash
# Build
mvn -pl juke-samples/juke-sample-greeting -am clean package

# Boot the sample
java -jar juke-samples/juke-sample-greeting/target/juke-sample-greeting-0.0.1-SNAPSHOT.jar &

# Begin a track
curl "http://localhost:8080/service/record/start?track=demo"

# Drive some traffic
for n in Alice Bob Charlie; do
    curl "http://localhost:8080/greeting?name=$n"
done

# End the track — streams the ZIP back; save it where any session can replay it
curl "http://localhost:8080/service/record/end" -o /tmp/juke-sample/demo.zip

# Replay it for one test session — no restart, no global mode flip
curl -c /tmp/cookies.txt "http://localhost:8080/service/session/start?track=demo"
curl -b /tmp/cookies.txt "http://localhost:8080/greeting?name=anyone"
# Returns the recorded "Hello, Alice!" — Alice was the first call in the recording

kill %1
```

Open `/tmp/juke-sample/demo.zip` in any zip tool and you'll see exactly the file structure described in §10.

### Inspect a recording inline

```bash
unzip -p /tmp/juke-sample/demo.zip IGreetingsService.greeting.1.json
# {"text":"Hello, Alice!"}

unzip -p /tmp/juke-sample/demo.zip IGreetingsService.greeting.1.args.json
# {"method":"greeting","paramTypes":["java.lang.String"],"arguments":["Alice"]}
```

### The dedicated coverage sample

`juke-samples/juke-sample-coverage/` is the canonical end-to-end demo for §11. It mounts a live coverage dashboard alongside a three-step user journey — every click moves the server bars (JaCoCo) on the right of the page, and a Playwright run fills in the UI bars on the next dashboard poll. Two launcher pairs handle the moving parts:

- `demo-start-server.ps1` / `demo-start-server.bat` — locates JDK 25, attaches `-javaagent:jacoco-agent.jar=output=none`, sets the `juke.coverage.classes` / `…sources` / `…report-dir` / `…ui-report-dir` keys, and boots the jar with `juke.enabled=true`.
- `demo-run-playwright.ps1` / `demo-run-playwright.bat` — runs the bundled journey spec twice (once **Formal**, once **Royal**), harvests `window.__coverage__` after each test, and writes the nyc report into `~/juke-demo/coverage/ui/` so the UI half of the dashboard fills in within two seconds.

Build the demo with the coverage profile first — `mvn -pl juke-samples/juke-sample-coverage -am package -Pcoverage -DskipTests` — then from inside the sample directory run the server launcher, browse to `http://localhost:8080`, and click through. See `juke-samples/juke-sample-coverage/README.md` for the full walk-through, including which lines are deliberately left uncovered so the `passed` badge flips green only on a complete journey.

---

## 13. Troubleshooting

### Recording is empty or only contains `juke.json`

- Check that `juke.enabled: true` is set in the active YAML. With the master toggle off, every Juke bean — including `RemixWebController`, the post-processors, and the session filter — is suppressed and `/service/record/start` never reaches your app.
- Check that the global mode is actually `record`. Even with `juke.enabled: true`, recording also requires the `juke=record` runtime mode (set via the YAML key `juke: record` or `JUKE_MODE=record`).
- Check that the bean carrying `@Juke` is being **injected through Spring**. `new MyService()` directly bypasses the post-processor that wraps it.
- Check the component scan covers `org.juke.framework` — without it the post-processor doesn't register and `@Juke` is silently inert.

### Replay throws `JukeReplayNotFoundException`

The replay request asked for a sequence number the recording doesn't have. Two common causes:

- **Sequence drift** — the calling order changed between record and replay. If the recording had 3 `greeting` calls and now there's a 4th, the 4th throws. Re-record.
- **Track mismatch** — `juke.zip` points at a different track from what was actually recorded. Check `juke.path` and `juke.zip`.

### `INPUT MISMATCH` warnings on replay

`juke.args-validation` is comparing the live arguments against the recorded ones and they don't match. Three options:

- Re-record with the new argument shape.
- Set `juke.args-validation: off` to silence the comparison entirely.
- Set `juke.args-validation: strict` to fail loudly with a `JukeInputMismatchException` — useful in CI to catch drift early.

### `H2 / database` errors at startup

You're running a Community-only build but Spring is autoconfiguring JPA. Either you're including `juke-scenario-service` (Enterprise) without realising it, or `spring-boot-starter-data-jpa` is being dragged in transitively. The Community footprint is `juke-framework + juke-remix-rest-service + spring-boot-starter-web + spring-boot-starter-aop + jackson` — nothing else should appear in `mvn dependency:tree`.

### Two `@Bean` clashes for `jukeSessionRegistry`

You have both `juke-remix-rest-service` (Community) and `juke-scenario-service` (Enterprise) on the classpath, and the auto-configuration ordering didn't pick up the Enterprise override. Confirm `juke-scenario-service` is in autoconfig — its `BundleSessionConfiguration` is annotated `@AutoConfiguration(before = JukeConfiguration.class)` and registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. If you've shaded the jar, the imports file may have been dropped.

### Tracks bleed across Playwright workers

You're using `replay/start` for a process-wide track flip rather than the per-session API. Switch to `/service/session/start?track=...` and have each worker carry its own `JUKE_SESSION` cookie. See §8.

---

## Where to next

- **Plugin authoring** — write a plugin that adds a new capability (UI harness, use-case suggestion, etc.) — see the [Enterprise guide](ENTERPRISE_GUIDE.md), the plugin model is foundational and Apache 2.0.
- **Centralised recording library** — the Enterprise admin server pulls recordings from registered agents and indexes them for cross-team search — see `ENTERPRISE_GUIDE.md` §6.
- **Database-backed agent storage** — `juke.storage.backend=db` swaps the folder default for a JPA-backed `JukeStorage` — covered in `ENTERPRISE_GUIDE.md` §3.

If you only need record-and-replay against a single host, **everything in this guide is what you need**. The Enterprise pieces are strict additions; nothing in Community changes when you bring them in.
