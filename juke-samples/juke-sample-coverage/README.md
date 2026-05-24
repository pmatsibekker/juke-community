# juke-sample-coverage

End-to-end demo of the **`juke-coverage`** module. A small Spring Boot app, a React SPA, and a live coverage dashboard — all in one jar. Click through the user journey on the left of the page and watch both halves of the coverage figure climb on the right.

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Juke Coverage Demo                                                       │
├───────────────────────────────┬───────────────────────────────────────────┤
│  Step 1 of 3                  │  Live Coverage           [PASSED] / FAIL  │
│  Who are you?                 │                                           │
│  [ Ada Lovelace            ]  │  Server (JaCoCo)                          │
│  [ Next → ]                   │   Line        ███████████░░░  76.4 %      │
│                               │   Branch      ████████░░░░░░  62.5 %      │
│                               │   Instruction ███████████░░░  78.1 %      │
│                               │                                           │
│                               │  UI (nyc/Istanbul)                        │
│                               │   Lines       █████████░░░░░  68.0 %      │
│                               │   Statements  █████████░░░░░  67.4 %      │
│                               │   Functions   ███████░░░░░░░  60.0 %      │
│                               │   Branches    ██████░░░░░░░░  55.5 %      │
│                               │                                           │
│                               │  @Juke seams excluded:                    │
│                               │    com.example.coverage.IGreetingComposer │
│                               │      → com.example.coverage.GreetingCo... │
│                               │                                           │
│                               │  Open server drill-down ↗ · UI ↗          │
└───────────────────────────────┴───────────────────────────────────────────┘
```

The page polls `GET /service/coverage` every two seconds. Each click in the journey runs more server lines (and more UI lines, since the SPA was built with `vite-plugin-istanbul`), so the dashboard updates in front of you.

---

## What this sample is for

| Question | Where to look |
|---|---|
| What does the `/service/coverage` JSON look like? | The dashboard renders it field-by-field; raw response at `http://localhost:8080/service/coverage` |
| What gets excluded as a `@Juke` seam? | `IGreetingComposer` → `GreetingComposerImpl` — visible in the dashboard's "seams excluded" list |
| Why isn't server coverage 100%? | `Auditor.adminAudit()` and `Auditor.dumpHistory()` are intentionally never called by the demo flow; the `royal` branch in `GreetingService` requires you to pick "Royal" to fill in |
| Why isn't UI coverage 100%? | `AboutScreen.jsx` is mounted only via the footer's "About this demo" link; the default journey doesn't click it |
| How does the `passed` badge work? | Thresholds are set in `application.yml` (`juke.coverage.threshold.server.line=65`, `ui.lines=55`); completing the journey flips the badge green |

---

## Run it

### 1. Build (one time, ~2 min)

From the **repo root**:

```bash
mvn -pl juke-samples/juke-sample-coverage -am package -Pcoverage -DskipTests
```

> The `-Pcoverage` profile is **required**. It builds the React bundle with `vite-plugin-istanbul` so the SPA exposes `window.__coverage__`. A plain `mvn package` produces a fast, uninstrumented bundle that leaves the UI half of the dashboard empty.

### 2. Start the server

Pick whichever terminal you're in. From inside `juke-samples\juke-sample-coverage\`:

| Shell | Command |
|---|---|
| **cmd.exe** | `demo-start-server.bat` |
| **PowerShell** | `.\demo-start-server.ps1` &nbsp; *(or `.\demo-start-server.bat` — the `.\` is required)* |
| **Explorer** | Double-click `demo-start-server.bat`, or right-click `.ps1` → "Run with PowerShell" |

> **PowerShell gotcha:** running `demo-start-server.bat` without the `.\` prefix fails with `CommandNotFoundException`. PowerShell does not search the current directory by default — `.\` tells it to. Either prefix the script name or use the `.ps1` variant, which is more idiomatic in PowerShell anyway.

Either script:
- locates JDK 25 (tries `JAVA_HOME`, `~/.jdks/ms-25.0.3`, `C:\Program Files\Java\jdk-25`)
- attaches the JaCoCo agent (`-javaagent:target/jacoco-agent.jar=output=none`)
- turns on `juke.coverage.enabled=true` and points `juke.coverage.classes`, `…sources`, `…report-dir`, `…ui-report-dir` at this module's paths
- launches the jar with `juke.enabled=true`

### 3. Open the browser

```
http://localhost:8080
```

Both panels load. Click through:

1. Type your name → **Next →**
2. Pick a style (try **Formal** first, then **Royal** on a later pass — it's the less-traveled server branch)
3. **Generate greeting** → see the result, click **Start over**, repeat

Watch the dashboard:
- The **server** bars climb on the very first click — `GET /api/styles` and `GET /api/greeting` exercise the controller, service, and DAO immediately.
- The **UI** bars stay at "Not available" until step 4 below produces the first nyc report.
- Once both bars cross their thresholds the **`PASSED`** badge turns green.

### 4. (Optional) Drive the journey automatically + populate UI coverage

The dashboard's **UI** half needs an nyc report on disk. Either harvest it manually (drive the UI, then run nyc yourself) or — easier — run the Playwright spec, which harvests `window.__coverage__` after every test and generates the report:

Same rules as the server script — from inside `juke-samples\juke-sample-coverage\`:

| Shell | Command |
|---|---|
| **cmd.exe** | `demo-run-playwright.bat` |
| **PowerShell** | `.\demo-run-playwright.ps1` &nbsp; *(or `.\demo-run-playwright.bat`)* |

Chrome opens, the journey runs twice (once with **Formal**, once with **Royal**), then the script writes an nyc report into `~/juke-demo/coverage/ui/`. Within two seconds the dashboard's UI bars fill in.

---

## What's in the box

```
juke-sample-coverage/
├── pom.xml                            One module, two profiles (`coverage` flips
│                                      the React build script to build:coverage)
├── demo-start-server.bat / .ps1       Launchers with JaCoCo + juke.coverage.* preset
├── demo-run-playwright.bat / .ps1     Runs the spec, harvests UI coverage, exits
├── src/main/
│   ├── java/com/example/coverage/
│   │   ├── CoverageDemoApplication.java   Spring Boot main; scans org.juke.* too
│   │   ├── GreetingController.java        GET /api/styles, /api/greeting
│   │   ├── GreetingService.java           Style branches — formal / casual / royal
│   │   ├── GreetingDao.java               Field-level @Juke seam
│   │   ├── IGreetingComposer.java         The interface @Juke intercepts
│   │   ├── GreetingComposerImpl.java      Displaced impl — excluded from coverage
│   │   ├── Auditor.java                   audit() is called; admin* methods aren't
│   │   └── Greeting.java                  Response POJO
│   ├── resources/application.yml          juke.enabled + juke.coverage.threshold.*
│   └── web-app/                           Vite + React SPA
│       ├── package.json                   vite-plugin-istanbul as a devDep
│       ├── vite.config.js                 forceBuildInstrument under --mode coverage
│       └── src/
│           ├── App.jsx                    Layout: journey | dashboard | footer
│           ├── CoveragePanel.jsx          Polls /service/coverage; renders bars
│           ├── JourneyApp.jsx             Login → style → confirm → done
│           └── AboutScreen.jsx            Mounted only via the footer link
└── src/test/playwright/                   Optional Playwright journey runner
    └── e2e/
        ├── coverage-fixture.ts            Harvests window.__coverage__ per test
        ├── global-teardown.ts             nyc report → ~/juke-demo/coverage/ui
        └── full-journey.spec.ts           Runs the journey twice (Formal + Royal)
```

---

## Why coverage is realistically below 100%

A demo that always reports 100% would be useless — you'd never see the gate fail. This sample is deliberately rigged with two pockets of uncovered code on each side:

### Server (≈ 75% line, ≈ 60% branch on a typical journey)
- `Auditor.adminAudit()` and `Auditor.dumpHistory()` — never called by the demo flow
- `GreetingService.greet()` royal branch — only fires if the user picks "Royal"

### UI (≈ 60-70% lines, ≈ 50% branches)
- `AboutScreen.jsx` render function — reached only via the footer's About link
- Some `JourneyApp.jsx` error paths (the `error` branch in `ResultStep`) — fire only if the network call fails

To watch coverage approach 100% intentionally:
1. Run the journey **picking Royal** (covers the third server branch)
2. Click **About this demo** in the footer (covers `AboutScreen.jsx`)
3. Disable the server briefly mid-submit to fire the error branch (rare; skip this in most demos)

The `passed` threshold in `application.yml` is tuned so a normal "Formal + Casual" pass crosses the line — running Royal is a bonus, not a requirement.

---

## How to gate CI on this

Once thresholds are configured, every response carries `passed`:

```bash
curl -sf http://localhost:8080/service/coverage | jq -e '.passed'
```

`jq -e` exits non-zero when `passed` is `false`, so the line drops straight into a CI pipeline as a gate. When it fails, the `message` field describes exactly which metrics fell short:

```json
{
  "available": true,
  "passed": false,
  "message": "Coverage below threshold — line 58.2% < required 65.0%, branch 41.6% < required 50.0%.",
  ...
}
```

The dashboard renders that same message in red below each half — there's no ambiguity about what to fix.

---

## Related reading

- [`juke-coverage/README.md`](../../juke-coverage/README.md) — module reference (endpoints, exclusion mechanism, all config keys)
- [`COMMUNITY_GUIDE.md` §11](../../COMMUNITY_GUIDE.md) — coverage chapter in the framework guide
- [`juke-sample-greeting`](../juke-sample-greeting/README.md) — the broader record/replay sample this one is forked from
