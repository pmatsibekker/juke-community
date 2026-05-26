# juke-coverage

Optional companion module that exposes **functional test coverage** — how many lines of your server-side application and React SPA are exercised by Juke replay sessions and Playwright user journeys, rather than by unit tests.

Two questions answered by three endpoints:

| Question | Endpoint | Tool |
|---|---|---|
| Both answers in one call | `GET /service/coverage` | JaCoCo + nyc/Istanbul |
| Which server-side lines did real journeys reach? | `GET /service/coverage/server` | JaCoCo (in-process) |
| Which front-end lines did real journeys reach? | `GET /service/coverage/ui` | nyc / Istanbul |

Both endpoints always respond `200`. When coverage is unavailable (no agent attached, no Playwright run yet) the body carries `available: false` and a `message` that explains why — a coverage hiccup never breaks the host application.

---

## Prerequisites

| Requirement | Notes |
|---|---|
| `juke-framework` + `juke-remix-rest-service` on the classpath | With `juke.enabled: true`. |
| **Server coverage:** JaCoCo runtime agent | Start the JVM with `-javaagent:jacoco-agent.jar=output=none`. |
| **UI coverage:** instrumented SPA | Build with `mvn package -Pcoverage`; run the Playwright coverage spec. |

---

## Quick start

### 1. Add the dependency

```xml
<dependency>
    <groupId>org.juke.harnesss</groupId>
    <artifactId>juke-coverage</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Configure

```yaml
juke:
  coverage:
    enabled: true
    classes: /absolute/path/to/app/target/classes   # required for server coverage
    sources: /absolute/path/to/app/src/main/java    # optional — enables source highlighting
    report-dir: /tmp/juke-coverage/server           # where the HTML report is written
    ui-report-dir: /tmp/juke-coverage/ui            # where Playwright writes the nyc report
    bundle-name: My Application                     # display name in the HTML report
    threshold:
      server:
        line: 80          # optional — omit to skip gating on this metric
        branch: 60
        instruction: 75
      ui:
        lines: 70
        statements: 70
        functions: 65
        branches: 55
```

### 3. Start the server with the JaCoCo agent

```bash
java -javaagent:/path/to/jacoco-agent.jar=output=none \
     -jar your-application.jar
```

`output=none` tells JaCoCo not to write a file on exit — `juke-coverage` reads the live execution data in-process via `RT.getAgent()` instead.

### 4. Drive traffic — replay sessions accumulate coverage automatically

```bash
curl -c /tmp/cookies.txt "http://localhost:8080/service/session/start?track=happy-path"
curl -b /tmp/cookies.txt "http://localhost:8080/your-endpoint"
curl -b /tmp/cookies.txt "http://localhost:8080/service/session/stop"
```

Every session that runs against the server adds to the same JaCoCo figure — multi-run aggregation is automatic.

### 5. Read server coverage

```bash
curl http://localhost:8080/service/coverage/server
```

Open the drill-down HTML report in a browser:

```
http://localhost:8080/coverage/server/index.html
```

### 6. Run Playwright and read UI coverage

```bash
# Build the SPA with instrumentation
mvn package -Pcoverage -pl juke-samples/juke-sample-greeting -am

# Run the Playwright coverage spec (writes nyc output to ui-report-dir)
npx playwright test --project=Coverage

# Read the summary
curl http://localhost:8080/service/coverage/ui
```

Open the drill-down HTML report:

```
http://localhost:8080/coverage/ui/index.html
```

---

## API reference

### `GET /service/coverage` — combined

Returns both server-side and front-end summaries in one response. Useful for CI scripts that need both figures without making two requests, and for dashboard widgets that display a unified coverage panel.

**Response shape (`CombinedCoverageSummary`):**

| Field | Type | Description |
|---|---|---|
| `server` | object | Full `CoverageSummary` — see below. |
| `ui` | object | Full `UiCoverageSummary` — see below. |
| `passed` | boolean | `true` when both `server.passed` and `ui.passed` are `true`. |
| `generatedAt` | string | ISO-8601 timestamp of this combined call. |

**Example response:**

```json
{
  "server": {
    "available": true,
    "passed": true,
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
  },
  "ui": {
    "available": true,
    "passed": true,
    "tool": "nyc/Istanbul",
    "lines": 78.0,
    "statements": 77.4,
    "functions": 80.0,
    "branches": 62.5,
    "reportUrl": "/coverage/ui/index.html",
    "generatedAt": "2026-05-20T10:00:00Z"
  },
  "passed": true,
  "generatedAt": "2026-05-20T10:00:00Z"
}
```

Both sub-summaries carry their own `available` and `passed` flags. When one half is unavailable (e.g. the JaCoCo agent is not attached), only that half's `available` is `false` — the other half is unaffected. The top-level `passed` is `true` only when both halves pass; CI scripts that gate on only one half should use the dedicated endpoint instead.

---

### `GET /service/coverage/server`

Reads the JaCoCo agent in-process, analyses the application classes (excluding `@Juke`-mocked implementations — see below), renders an HTML report to `juke.coverage.report-dir`, and returns a JSON summary.

**Response shape (`CoverageSummary`):**

| Field | Type | Description |
|---|---|---|
| `available` | boolean | `false` when the agent is not attached or the classes directory is missing. |
| `passed` | boolean | `true` when all configured thresholds are met (or no thresholds are set). Use this field in CI scripts. |
| `message` | string | Human-readable status. When `passed` is `false`, lists each metric that missed its threshold. |
| `tool` | string | `"JaCoCo"` |
| `instruction` | number | Instruction coverage, 0–100. |
| `branch` | number | Branch coverage, 0–100. |
| `line` | number | Line coverage, 0–100. |
| `analyzedClasses` | number | Number of application classes counted, after `@Juke` exclusions. |
| `excludedSeams` | string[] | `"interfaceFqn -> implFqn"` pairs excluded from the count. |
| `reportUrl` | string | Public path to the generated HTML report. |
| `generatedAt` | string | ISO-8601 timestamp of this run. |

**Example response:**

```json
{
  "available": true,
  "passed": true,
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

---

### `GET /service/coverage/ui`

Reads the `coverage-summary.json` written by `nyc` after the latest Playwright run and returns a JSON summary.

**Response shape (`UiCoverageSummary`):**

| Field | Type | Description |
|---|---|---|
| `available` | boolean | `false` when no `coverage-summary.json` has been written yet. |
| `passed` | boolean | `true` when all configured thresholds are met (or no thresholds are set). Use this field in CI scripts. |
| `message` | string | Human-readable status. When `passed` is `false`, lists each metric that missed its threshold. |
| `tool` | string | `"nyc/Istanbul"` |
| `lines` | number | Line coverage, 0–100. |
| `statements` | number | Statement coverage, 0–100. |
| `functions` | number | Function coverage, 0–100. |
| `branches` | number | Branch coverage, 0–100. |
| `reportUrl` | string | Public path to the generated HTML report. |
| `generatedAt` | string | ISO-8601 timestamp of this read. |

**Example response:**

```json
{
  "available": true,
  "passed": true,
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

---

## How `@Juke` seams are excluded

In Juke replay mode, the implementation class behind a `@Juke`-annotated field is never called — the framework's proxy intercepts every call and returns the recorded response directly. Counting those displaced classes in the coverage figure would unfairly depress it.

`juke-coverage` solves this without requiring any developer configuration. When `juke-framework` creates a proxy it registers the displaced implementation in `JukeMockRegistry`. The JaCoCo analyser in `JacocoCoverageService` skips every class named in that registry, plus any of their nested classes. The `excludedSeams` field in the response lists exactly what was excluded, so the figure is always transparent and reproducible.

---

## Configuration reference

| Key | Default | Notes |
|---|---|---|
| `juke.coverage.enabled` | `false` | Opt-in gate — all coverage beans are conditional on this. Absent = off. |
| `juke.coverage.classes` | (none) | Absolute path to the application's compiled classes. Required for server coverage. |
| `juke.coverage.sources` | (none) | Absolute path to `src/main/java`. Optional; enables source-line highlighting. |
| `juke.coverage.report-dir` | `${user.home}/juke-demo/coverage/server` | Directory the server HTML report is written into. Also served as `/coverage/server/**`. |
| `juke.coverage.ui-report-dir` | `${user.home}/juke-demo/coverage/ui` | Directory the nyc report is read from. Also served as `/coverage/ui/**`. |
| `juke.coverage.bundle-name` | `Application under test` | Display name for the JaCoCo bundle in the HTML report. |
| `juke.coverage.threshold.server.line` | `0` | Minimum server line coverage (%). `0` = no gate. |
| `juke.coverage.threshold.server.branch` | `0` | Minimum server branch coverage (%). `0` = no gate. |
| `juke.coverage.threshold.server.instruction` | `0` | Minimum server instruction coverage (%). `0` = no gate. |
| `juke.coverage.threshold.ui.lines` | `0` | Minimum UI line coverage (%). `0` = no gate. |
| `juke.coverage.threshold.ui.statements` | `0` | Minimum UI statement coverage (%). `0` = no gate. |
| `juke.coverage.threshold.ui.functions` | `0` | Minimum UI function coverage (%). `0` = no gate. |
| `juke.coverage.threshold.ui.branches` | `0` | Minimum UI branch coverage (%). `0` = no gate. |

---

## CI gating

Every response carries a top-level `passed` boolean. A CI script can fail the build on a single `jq` call without parsing percentage fields:

```bash
# Gate on combined (server + UI) — fails if either half is below threshold
curl -sf http://localhost:8080/service/coverage | jq -e '.passed'

# Gate on server only
curl -sf http://localhost:8080/service/coverage/server | jq -e '.passed'

# Gate on UI only
curl -sf http://localhost:8080/service/coverage/ui | jq -e '.passed'
```

`jq -e` exits with a non-zero status when the expression evaluates to `false` or `null`, so the command works directly as a pipeline step. When a threshold is missed, `message` in the JSON body describes every metric that fell short:

```json
{
  "available": true,
  "passed": false,
  "message": "Coverage below threshold — line 74.3% < required 80.0%, branch 55.1% < required 60.0%.",
  ...
}
```

---

## Production safety

Keep `juke.coverage.enabled` absent from `application-prod.yml`. When the property is missing or `false`:

- `CoverageAutoConfiguration` is skipped entirely — no beans are registered.
- The `/service/coverage/*` endpoints do not exist.
- The `-javaagent` flag should likewise be absent from production JVM launch commands.

The jar may remain on the classpath across all environments; the feature is invisible until explicitly opted in per environment.

---

## Module structure

```
org.juke.coverage
├── CoverageAutoConfiguration   Spring Boot auto-configuration; all beans conditional on juke.coverage.enabled
├── CoverageController          REST controller — GET /service/coverage, /server, /ui
├── JacocoCoverageService       Reads JaCoCo agent in-process; analyses classes; renders HTML
├── UiCoverageService           Reads coverage-summary.json written by nyc after a Playwright run
├── CoverageThresholds          Minimum acceptable percentages; evaluated when building each summary
├── CoverageSummary             Immutable record — server coverage JSON response
├── UiCoverageSummary           Immutable record — UI coverage JSON response
└── CombinedCoverageSummary     Immutable record — combined server + UI JSON response
```
