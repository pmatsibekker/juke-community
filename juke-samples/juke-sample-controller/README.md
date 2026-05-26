# juke-sample-controller

Reference implementation for **`@JukeController`** — controller-level capture and
contract-drift detection.

## What it validates

`GreetController` is annotated `@JukeController`. The framework's AOP advice:
- **records** the controller's request + response into the recording (two
  `controller-capture/` sidecars per call), and
- on **replay** diffs each call against the baseline, logging
  `CONTROLLER_MISMATCH [GreetController.greet#step]` when the live call
  deviates. The advice **never changes the response** — it observes and reports.

The demo shows the two outcomes:
- **Clean replay** — same input (`Alice`) → matches the baseline → no finding.
- **Poisoned replay** — different input (`Bob`) → deviates → a
  `CONTROLLER_MISMATCH` is logged. `replay/start` resets the per-class step
  counter, so the poisoned call is step 1 again and is diffed against the
  recorded `Alice` baseline.

> `@JukeController` advice fires under the **global** replay mode (or an active
> cookie session), so this sample drives global record → replay via
> `/service/record/*` and `/service/replay/*`.

## How it's verified

- **`ControllerCaptureTest`** (`mvn test`) — records a baseline, then replays
  clean and poisoned through `/service/*`, capturing logs to assert that
  `CONTROLLER_MISMATCH` is logged **only** for the poisoned run while the live
  response is returned in both. Headless, unit-testable gate.
- **Visible UI** (`src/main/resources/static/index.html`, plain HTML — no npm) —
  a banner explains the feature; the steps (Record → Replay clean → Replay
  poisoned) show the live response and whether it matches the baseline.

## Run it

```bash
mvn -o -pl juke-samples/juke-sample-controller -am test
# or boot it and open http://localhost:8080
mvn -pl juke-samples/juke-sample-controller spring-boot:run
```

### Two-window curl demo (Windows)

Build the jar once, then run the server in one window and the curl client in
another. The poisoned replay logs `CONTROLLER_MISMATCH` in the **server**
window (the advice reports drift server-side and never alters the response):

```bat
mvn -pl juke-samples/juke-sample-controller -am package -DskipTests

:: window 1 — start the server (leave it open, watch for CONTROLLER_MISMATCH)
juke-samples\juke-sample-controller\demo-start-server.bat

:: window 2 — drive record -> clean replay -> poisoned replay
juke-samples\juke-sample-controller\demo-run-curl.bat
```

PowerShell equivalents: `demo-start-server.ps1` and `demo-run-curl.ps1`.
