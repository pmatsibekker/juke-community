# juke-sample-status-grid

Reference implementation for **cross-session status visibility** — the
`GET /service/sessions` aggregate view that drives a live multi-worker status
grid.

## What it validates

One recording, many concurrent replay sessions. The seam is an interface field
recorded globally and replayed per cookie session:

```java
@Juke
@Autowired
GreetingService greetingService;     // GreetController
```

- **Per-session cursors** — each `JUKE_SESSION_ID` cookie advances its own
  position through the same track, independently of every other session.
- **`lastCall`** — each session reports the recording entry it most recently
  handed back (e.g. `GreetingService.greet.4`) and the sequence number.
- **`percentComplete`** — each session reports how far through the recording it
  is (cursor position / total recorded calls), so the grid can show progress
  bars for workers at different positions.

> `@Juke` on an **interface** field records globally and replays
> **per session**, so this sample records once via `/service/record/*` and then
> replays through cookie sessions via `/service/session/*`.

## How it's verified

- **`StatusGridSessionsTest`** (`mvn test`) — records a six-call track, then
  starts two independent cookie sessions and drives a different number of calls
  through each (2 vs 4). It manages two cookie jars by hand (capturing
  `Set-Cookie` from `/service/session/start` and replaying them per call — what
  a browser does per tab), then asserts `GET /service/sessions` reports both
  sessions with distinct `lastCall.sequence` (2 vs 4) and ordered, strictly
  partial `percentComplete` (50% vs 83.33%). Headless, unit-testable gate.
- **Visible UI** (`src/main/resources/static/index.html`, plain HTML — no npm) —
  a banner explains the feature; Record then Spawn worker repeatedly, and the
  grid (with optional auto-refresh) renders each session's last call and a
  progress bar from the live `/service/sessions` payload.

## Run it

```bash
# Build + run the verification gate
mvn -o -pl juke-samples/juke-sample-status-grid -am test

# Or boot it and open the UI at http://localhost:8080
mvn -pl juke-samples/juke-sample-status-grid spring-boot:run
#   1. Record track (6 calls)  → one greet seam, sequences 1–6
#   2. Spawn worker (repeat)    → each starts a cookie session, replays a slice
#   Refresh grid / auto-refresh → lastCall + percentComplete per session
```
