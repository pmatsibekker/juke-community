# 🎵 Juke — Record. Replay. Ship with Confidence.

**Juke** is a lightweight Java framework that **records** real service interactions and **replays** them deterministically — eliminating hand-written mocks and bridging the gap between UI and server-side testing.

> *Stop writing code to test code. Just press record.*

| | |
|---|---|
| **Language** | Java 25 · Spring Boot 3.5 |
| **Build** | Maven multi-module |
| **License** | See [LICENSE](LICENSE) |
| **Status** | 0.0.1-SNAPSHOT — Active Development |

---

## The Problem

Modern applications split logic across UI and server layers, but testing frameworks don't.

- **UI test tools** (Playwright, Cypress, Selenium) test the browser — but ignore server-side logic changes.
- **Unit test frameworks** test server code — but require extensive hand-written mocks that are expensive to build and fragile to maintain.
- **Neither approach** validates the full use-case end-to-end with realistic data.

The result? Teams spend as much time writing and maintaining test code as they do writing production code — and still ship with gaps.

## The Solution

Juke sits inside your Spring Boot application and transparently proxies your service layer. In **record** mode, it captures every upstream interaction as real JSON data stored in a portable ZIP archive. In **replay** mode, it serves those recordings back — giving you a deterministic, stateful mock layer built from real data, with zero hand-written test code.

```
┌─────────┐       ┌──────────────────┐       ┌──────────────┐
│ Browser  │──────►│  Your App Code   │──────►│  Upstream    │
│ / Tests  │       │  (Spring Boot)   │       │  Services    │
└─────────┘       └────────┬─────────┘       └──────────────┘
                           │
                    ┌──────┴──────┐
                    │    Juke     │
                    │  Framework  │
                    │             │
                    │ RECORD: ────┼──► Captures real responses to ZIP
                    │ REPLAY: ────┼──► Serves recorded data back
                    │ REMIX:  ────┼──► Injects delays, exceptions, errors
                    └─────────────┘
```

---

## ✨ Key Features

### 🎤 Zero-Code Test Data
Record real upstream interactions — no hand-written mocks, no fixture files, no boilerplate. Your test data is always realistic because it came from a real system.

### 🔄 Deterministic Replay
Replay recorded interactions in exact sequence order. Your tests return the same data every time, regardless of upstream availability or state changes.

### 🎯 Three Proxy Strategies
Juke handles virtually every Spring injection pattern:

| Strategy | Annotation | Use Case |
|---|---|---|
| **JDK Dynamic Proxy** | `@Juke` on interface fields | Interface-typed dependencies (`IGreetingsService`) |
| **CGLIB Subclass Proxy** | `@Juke` on classes | Concrete `@Service` / `@Component` beans |
| **CGLIB Subclass Proxy** | `@Juke` on concrete fields | Spring templates & other concrete beans (`RestTemplate`, `JdbcTemplate`) |

### 🎛️ Remix — Runtime Behaviour Injection
Inject failures and delays via REST API — no code changes required:

- **Exception Injection** — Simulate `IOException`, `TimeoutException`, or any throwable on a specific method call
- **Delay Injection** — Add latency to specific interactions to test timeout handling and resilience

### 📦 Portable Recordings
Recordings are single ZIP files containing human-readable JSON. Commit them to version control, share across teams, or embed in CI/CD pipelines.

### 🔌 Minimal Integration
Add a single annotation to your existing code:

```java
// Interface-based (JDK proxy)
@Autowired
@Juke
private IGreetingsService greetingService;

// Class-based (CGLIB proxy)
@Juke
@Service
public class OrderService implements Billable, Shippable { ... }

// Concrete field (CGLIB subclass)
@Autowired
@Juke
private RestTemplate restTemplate;
```

### 🧪 Playwright-Ready
Juke includes a built-in **Playwright Comparison Engine** for automated regression detection across UI test recordings, with support for approved-ignore paths.

### 📊 Functional Test Coverage
The optional `juke-coverage` module answers "which lines did real user journeys reach?" — not just unit tests. It accumulates live JaCoCo data across every Juke replay session (server-side, no separate coverage build) and reads nyc/Istanbul reports from Playwright runs (front-end). Results are available as JSON via `GET /service/coverage/server` and `GET /service/coverage/ui`, with drill-down HTML reports served on the same host.

---

## How Juke Compares

| Capability | Juke | Playwright / Cypress / Selenium | WireMock / MockServer | Hand-Written Mocks |
|---|:---:|:---:|:---:|:---:|
| Tests UI + server together | ✅ | ❌ UI only | ❌ Server only | ❌ Server only |
| Uses real recorded data | ✅ | ⚠️ UI-layer only | ❌ Manual setup | ❌ Manual setup |
| Zero test code required | ✅ | ⚠️ Some scripting | ❌ Config files | ❌ Extensive code |
| Detects server-side regressions | ✅ | ❌ | ❌ | ⚠️ If tests cover it |
| Runtime fault injection | ✅ via REST | ❌ | ✅ | ⚠️ Requires code |
| Portable test fixtures | ✅ ZIP files | ❌ | ⚠️ JSON mappings | ❌ |
| Spring Boot native | ✅ | ❌ | ⚠️ Separate process | ✅ |
| Works with UI test frameworks | ✅ Complements them | N/A | ⚠️ | ❌ |

> **Juke doesn't replace UI testing frameworks — it complements them.** Use Playwright, Cypress, or Selenium for browser automation while Juke provides a deterministic, controllable server-side data layer underneath.

---

## 🚀 Quick Start

### Prerequisites

- **Java** 25
- **Maven** 3.9+
- **Node.js** 20+ (for the sample UI application; auto-installed by `frontend-maven-plugin` on first build)

### 1. Build the Project

```bash
git clone https://github.com/pmatsibekker/juke-community.git
cd juke-community
mvn clean install
```

### 2. Configure Your Application

Add the Juke component scan to your Spring Boot application:

```java
@SpringBootApplication
@ComponentScan(basePackages = {"org.juke.remix", "org.juke.framework", "com.example"})
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

### 3. Annotate Your Services

```java
@Service
public class MyDAO {

    @Juke
    private IGreetingsService greetingService;

    @Juke
    private IAnotherService anotherService;

    @Autowired
    public MyDAO(IGreetingsService greetingService, IAnotherService anotherService) {
        this.greetingService = greetingService;
        this.anotherService = anotherService;
    }
}
```

### 4. Record

With the app running, start a recording over REST — no restart needed:

```bash
# Begin capturing under a track name
curl "http://localhost:8080/service/record/start?track=my-test"

# Drive traffic normally — every @Juke-wrapped call is captured
curl "http://localhost:8080/your-endpoint"

# End the track — streams the ZIP back
curl "http://localhost:8080/service/record/end" -o my-test.zip
```

Drop `my-test.zip` into `juke.path` and it is immediately available for replay.

### 5. Replay

Start a per-session replay — no restart, multiple workers can each hold a different track simultaneously:

```bash
# Start a session and capture the JUKE_SESSION cookie
curl -c /tmp/cookies.txt "http://localhost:8080/service/session/start?track=my-test"

# Drive your tests — every request carrying the cookie replays from my-test.zip
curl -b /tmp/cookies.txt "http://localhost:8080/your-endpoint"

# End the session
curl -b /tmp/cookies.txt "http://localhost:8080/service/session/stop"
```

Your tests now run against recorded data — deterministic every run, regardless of upstream availability.

---

## ⚙️ Configuration

Juke supports layered configuration via **YAML files** with Spring-style profile overrides, plus backward-compatible **VM arguments**.

### YAML Configuration (Recommended)

```yaml
# application.yml
juke:
  mode: ignore          # record | replay | ignore | disable
  path: /data/juke      # Directory for ZIP recordings
  zip: track            # ZIP file name (without .zip extension)
  disabled: false       # Master kill-switch
```

**Profile layering** — later profiles override earlier ones:

```
juke-defaults.yml          → Framework defaults (shipped in JAR)
application.yml            → Your app base config
application-record.yml     → Sets juke.mode=record
application-replay.yml     → Sets juke.mode=replay
application-local.yml      → Local environment paths
```

Activate profiles:

```bash
java -Dspring.profiles.active=local,record -jar my-app.jar
```

### VM Arguments (Legacy)

| Argument | Values | Description |
|---|---|---|
| `-Djuke` | `record`, `replay`, `ignore` | Operating mode (default: `ignore`) |
| `-Djuke.path` | Directory path | Where ZIP recordings are stored |
| `-Djuke.zip` | File name | ZIP file name without `.zip` extension |
| `-Djuke.tests` | Comma-separated names | Whitelist of allowed recording names (security) |

---

## 🌐 REST API Reference

All endpoints are available when `juke.enabled: true`. No Spring profile required — the mode is driven over REST at runtime with no JVM restart.

### Recording Control

| Endpoint | Description |
|---|---|
| `GET /service/record/start?track={name}` | Begin recording a new track |
| `GET /service/record/end` | Stop recording, flush to ZIP, download |

### Replay Control

| Endpoint | Description |
|---|---|
| `GET /service/replay/start?track={name}` | Switch to a named recording for replay |
| `GET /service/replay/enable` | Re-enable replay after disabling |
| `GET /service/replay/disable` | Temporarily pass through to real services |

### Remix — Behaviour Injection

| Endpoint | Description |
|---|---|
| `GET /service/remix/exceptionSchedule?classAndMethodSequence={id}&exception={type}&exceptionMessage={msg}` | Inject an exception on a specific method invocation |
| `GET /service/remix/delaySchedule?classAndMethodSequence={id}&waitTimeInMS={ms}` | Inject a delay on a specific method invocation |
| `GET /service/remix/clear` | Clear all scheduled delays/exceptions — call between replay runs so injected faults don't compound |

**Example — Inject an IOException on the first greeting call:**

```
GET http://localhost:8080/service/remix/exceptionSchedule
    ?classAndMethodSequence=IGreetingsService.greeting.1
    &exception=IOException
    &exceptionMessage=Simulated+upstream+failure
```

**Example — Add a 10-second delay to the first greeting call:**

```
GET http://localhost:8080/service/remix/delaySchedule
    ?classAndMethodSequence=IGreetingsService.greeting.1
    &waitTimeInMS=10000
```

### Sessions — Per-Track Replay

| Endpoint | Description |
|---|---|
| `GET /service/session/start?track={name}` | Start a cookie-scoped per-track replay session. Sets a `JUKE_SESSION` cookie — multiple workers can each hold a different track simultaneously. |
| `GET /service/session/stop` | End the active session and clear its cookie. |
| `GET /service/sessions` | Live view of every active session — track, progress, and last served call. |

### Recordings Catalog

| Endpoint | Description |
|---|---|
| `GET /service/recordings` | List every recording the agent holds. |
| `GET /service/recordings/{name}` | Stream the named recording's ZIP bytes. |

### Coverage (optional — requires `juke-coverage`)

| Endpoint | Description |
|---|---|
| `GET /service/coverage/server` | Live server-side (JaCoCo) coverage summary. HTML report at `/coverage/server/index.html`. |
| `GET /service/coverage/ui` | Front-end (nyc/Istanbul) coverage summary from the latest Playwright run. HTML report at `/coverage/ui/index.html`. |

---

## 📂 Recording Format

Recordings are stored as ZIP archives containing human-readable JSON files:

```
my-test.zip
├── IGreetingsService.greeting.1.json          # First call response
├── IGreetingsService.greeting.1.args.json     # Input arguments (sidecar)
├── IGreetingsService.greeting.1.type.json     # Type metadata (sidecar)
├── IGreetingsService.greeting.2.json          # Second call response
├── IGreetingsService.greeting.2.args.json
├── juke.json                                  # Interface/class metadata manifest
└── juke-mappings.json                         # Short name → FQN lookup
```

Recordings are **portable** — commit them to your repository, share them across teams, or bundle them with your CI/CD pipeline.

---

## 🧪 Running the Sample Application

The included sample demonstrates Juke with a Spring Boot REST service and a React UI.

> **Prefer a guided walk-through?** [`juke-samples/DEMO.md`](juke-samples/DEMO.md)
> is a 15-minute, copy-pasteable demo of all four key flows
> (deterministic record/replay, input-mismatch detection,
> false-positive handling, and a visible Playwright run). The steps
> below are the shorter "just show me the sample" version.

### Step 1 — Build

```bash
cd juke-community
mvn clean install
```

### Step 2 — Pick a sample

Each sample under `juke-samples/` is a self-contained Spring Boot app
demonstrating one Juke pattern. The greeting sample bundles a React UI
into the same jar, so launching it serves both the REST endpoint and the
browser UI from one JVM:

```bash
mvn -pl juke-samples/juke-sample-greeting spring-boot:run
# Browse http://localhost:8080/ for the React UI; /greeting?name=… for the REST endpoint.
```

The full lineup:

| Module | What it demonstrates |
|---|---|
| `juke-samples/juke-sample-greeting` | Basic record/replay against a Spring REST controller via `@Juke`-annotated DAO, with an integrated React UI |
| `juke-samples/juke-sample-session` | Cookie-bound per-session replay; Playwright cookie-isolation specs included |
| `juke-samples/juke-sample-todo` | REST CRUD wired through a `@Juke` DAO |
| `juke-samples/juke-sample-annotations` | Reference for field-, method-, and constructor-level `@Juke` usage |
| `juke-samples/juke-sample-coverage` | Live functional-coverage dashboard (JaCoCo server + nyc/Istanbul UI) that updates as you click through a journey |
| `juke-samples/juke-sample-exceptions` | Exception/latency flows: record → replay → replay+delay ("queued") → replay+exception ("technical difficulties"), with `@JukeIgnorable` confirmation numbers and a coverage popup |

See [`juke-samples/README.md`](juke-samples/README.md) for the full per-module
README, the standard `curl` recipe for record / replay, and the rationale
behind splitting the previous mono-sample into focused modules.

### Step 3 — Drive a recording

With the greeting sample running, ask the framework to begin capturing:

```bash
curl 'http://localhost:8080/service/record/start?track=demo'
# Issue some requests through the React UI or curl directly.
curl 'http://localhost:8080/greeting?name=Alice'
curl 'http://localhost:8080/service/record/end' -o /tmp/demo.zip
```

### Step 4 — Replay an isolated session

```bash
curl -c /tmp/cookies.txt 'http://localhost:8080/service/session/start?track=demo'
curl -b /tmp/cookies.txt 'http://localhost:8080/greeting?name=anyone'
# Returns "Hello, Alice!" — the first recorded response.
```

### Step 5 — Run the Playwright tests

The cookie-isolation Playwright specs live with the session sample:

```bash
mvn -pl juke-samples/juke-sample-session spring-boot:run    # in one terminal
cd juke-samples/juke-sample-session/src/test/playwright     # in another
npx playwright test --ui
```

> **Tip:** to record new Playwright tests, install the [Playwright VS Code extension](https://playwright.dev/docs/getting-started-vscode) and use the built-in test recorder.

---

## 🏗️ Architecture

Juke is a multi-module Maven project:

```
juke-test-harness (parent POM)
│
├── juke-framework              Core library — annotations, proxies, persistence,
│                                configuration, tuners, scheduling, Playwright engine
│
├── juke-remix-rest-service     REST API for runtime control of record/replay/remix
│
├── juke-coverage               Optional — functional test coverage (JaCoCo server-side +
│                                nyc/Istanbul front-end) via /service/coverage/* endpoints
│
├── juke-plugin-api             Plugin contract (DTOs over /service/plugins/*)
├── juke-plugin-sdk             Spring Boot starter for plugin authors
│
└── juke-samples                Aggregator of focused sample modules
    ├── juke-sample-greeting        Basic @Juke wiring + integrated React UI
    ├── juke-sample-session         Cookie-bound replay + Playwright specs
    ├── juke-sample-todo            REST CRUD with @Juke
    ├── juke-sample-annotations     @Juke usage-pattern reference
    ├── juke-sample-coverage        Live functional-coverage dashboard
    └── juke-sample-exceptions      Fault flows (delay/exception) + coverage popup
```

For a detailed technical deep-dive, see [DESIGN_ANALYSIS.md](DESIGN_ANALYSIS.md).

---

## 🔧 Technology Stack

| Component | Technology |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 3.5, Spring Framework 6.x |
| Serialization | Jackson 2.14 (JSON) |
| Storage | ZIP archives (`java.util.zip`) |
| Proxy | JDK Dynamic Proxy, Spring CGLIB |
| Configuration | YAML (SnakeYAML) with Spring profile support |
| UI Testing | Playwright |
| Build | Maven |
| Testing | JUnit 5, Mockito |
| Coverage | JaCoCo |

---

## 📖 Further Reading

- [COMMUNITY_GUIDE.md](COMMUNITY_GUIDE.md) — Complete reference for annotations, modes, sessions, Remix, coverage, and configuration
- [JUKE_MANUAL.md](JUKE_MANUAL.md) — desk-reference manual; good starting point for a structured read-through
- [juke-samples/DEMO.md](juke-samples/DEMO.md) — 15-minute copy-pasteable walkthrough of all key flows
- [juke-coverage/README.md](juke-coverage/README.md) — Functional test coverage setup, API reference, and configuration
- [DESIGN_ANALYSIS.md](DESIGN_ANALYSIS.md) — Full architectural deep-dive with class inventory and data flow diagrams
- [Playwright Documentation](https://playwright.dev/docs/intro) — Getting started with Playwright
- [Playwright VS Code Extension](https://playwright.dev/docs/getting-started-vscode) — Record tests directly from your editor

---

## 📄 License

See the [LICENSE](LICENSE) file for details.
