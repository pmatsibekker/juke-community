# juke-sample-exceptions

A demo of how Juke handles **exception and latency flows**. A shopper buys a product; the order
service places **three orders** with an external Order Management System (OMS) reached through a
`@Juke` seam. The same journey is driven **four times** to show record, deterministic replay, an
injected delay, and an injected exception — all without changing a line of application code.

```
┌────────────────────────────────────────────────────────────┐
│  Juke Store                              [ View test coverage ]│
│  Buy a product — we place your order with the OMS.           │
├──────────────────────────────────────────────────────────────┤
│  Choose a product                                            │
│  [ Lyrebird Figurine ] [ 7" Vinyl Single ] [ R&R Mug ]       │
│                                                              │
│  [  Buy Lyrebird Figurine  ]   Placing order 2 of 3…         │
│                                                              │
│  Orders                                                      │
│   Order 1 of 3   COMPLETED   JK-D6A5A4   OMS-E9A0C04         │
│   Order 2 of 3   QUEUED      JK-8B094E                       │
│   Order 3 of 3   COMPLETED   JK-A3A23D   OMS-6727C37         │
└──────────────────────────────────────────────────────────────┘

         ┌──────────────────────────────────────┐
         │  Order 2 of 3                         │   ← 5-second popup,
         │  Your order is queued                 │     one per order
         │  Your order is queued and will be     │
         │  confirmed shortly.                   │
         │  Confirmation number:  JK-8B094E      │
         └──────────────────────────────────────┘
```

Each order produces a **5-second confirmation popup**. Coverage statistics live in a **separate
popup** (the "View test coverage" button), deliberately kept off the ordering screen.

---

## The four runs

| Run | Mode | What happens | UI result |
|---|---|---|---|
| **1** | Record | Global record mode captures the 3 OMS calls into `order-demo.zip` | 3 × **COMPLETED** popups |
| **2** | Replay (per-session) | The recorded receipts replay deterministically | 3 × **COMPLETED**, same OMS order ids as run 1 |
| **3** | Replay + **delay** | Remix injects a 10s delay on the **2nd** order; the client times out at 4s | order 2 → **"Your order is queued"**, orders 1 & 3 complete |
| **4** | Replay + **exception** | Remix injects an `IOException` on the **2nd** order; the service catches it | order 2 → **"We are experiencing technical difficulties…"**, orders 1 & 3 complete |

The Playwright spec (`order-flow.spec.ts`) drives all four in one headed, slowed-down browser so
you can watch every popup, then opens the coverage popup and holds the drill-down report on screen
for >10 seconds.

---

## What this sample is for

| Question | Where to look |
|---|---|
| How does Juke replay a fault on **one specific call**? | Remix `delaySchedule` / `exceptionSchedule` on `IOrderManagementSystem.submitOrder.2`; the spec derives that id at runtime from `GET /service/recording/inputs` |
| How do I keep a **non-deterministic field** from breaking replay? | The confirmation number is generated fresh per attempt and marked `@JukeIgnorable` on `OmsOrderRequest`; the run-2 session report stays `COMPLETED` even though the number differs every run |
| Why is "queued" handled **client-side**? | A `@Juke` call must stay on the HTTP request thread (the replay/session context is request-scoped), so the server can't offload it to time out. The SPA's axios timeout (4s) detects the slow 2nd order and shows "queued"; the confirmation number is client-generated so it's available even before the server responds |
| What does the **graceful exception** path look like? | `OrderService.placeOrder` catches the OMS failure and returns `status: RECORDED` — the customer still gets their confirmation number and a "recorded for later" message |
| What gets **excluded** from coverage? | `IOrderManagementSystem → OrderManagementSystemImpl` (the displaced `@Juke` seam impl), shown under "@Juke seams excluded" in the coverage popup |

---

## Run it

### 1. Build (one time)

From the **repo root**:

```bash
mvn -pl juke-samples/juke-sample-exceptions -am package -Pcoverage -DskipTests
```

> The `-Pcoverage` profile is **required** — it builds the React bundle with `vite-plugin-istanbul`
> so the SPA exposes `window.__coverage__` for the UI half of the coverage popup. A plain
> `mvn package` ships a fast, uninstrumented bundle. The bundle is emitted straight into
> `target/classes/static`, so `mvn clean` always wipes stale output.

### 2. Start the server

From inside `juke-samples\juke-sample-exceptions\`:

| Shell | Command |
|---|---|
| **cmd.exe** | `demo-start-server.bat` |
| **PowerShell** | `.\demo-start-server.ps1` &nbsp; *(the `.\` is required)* |
| **Explorer** | Double-click `demo-start-server.bat`, or right-click `.ps1` → "Run with PowerShell" |

The script locates JDK 25, attaches the JaCoCo agent, turns on `juke.coverage.enabled=true`, and
launches the jar with `juke.enabled=true`, `juke.path=~/juke-demo/sample-exceptions`, and
`juke.zip=order-demo`.

### 3. Open the browser

```
http://localhost:8080
```

Pick a product, click **Buy**, and watch three orders process with a 5-second popup each. Click
**View test coverage** for the coverage popup (Summary tab → server + UI bars and the excluded
seam; **Coverage report** tab → the JaCoCo drill-down).

### 4. Drive all four runs automatically

In a **second** terminal, from inside `juke-samples\juke-sample-exceptions\`:

| Shell | Command |
|---|---|
| **cmd.exe** | `demo-run-playwright.bat` |
| **PowerShell** | `.\demo-run-playwright.ps1` |

Chrome opens headed and slowed down; the four runs play out in sequence, then the coverage popup
opens and the report is held on screen. UI coverage is harvested into `~/juke-demo/coverage/ui/`
so the popup's UI half fills in.

> **Headless / CI:** set `JUKE_HEADLESS=1` to run the spec without a visible browser (the demo is
> headed + `slowMo` by default so it's watchable).

---

## What's in the box

```
juke-sample-exceptions/
├── pom.xml                              One module; `coverage` profile builds the
│                                        instrumented SPA. Vite outputs to target/classes/static.
├── demo-start-server.bat / .ps1         Launcher: JDK 25 + JaCoCo + juke.coverage.* preset
├── demo-run-playwright.bat / .ps1       Runs the four-run spec, harvests UI coverage
├── src/main/
│   ├── java/com/example/exceptions/
│   │   ├── OrderApplication.java         Spring Boot main; scans org.juke.framework + org.juke.remix
│   │   ├── OrderController.java          GET /api/products, POST /api/order
│   │   ├── OrderService.java             @Juke("juke") OMS seam; try→COMPLETED / catch→RECORDED
│   │   ├── IOrderManagementSystem.java   The seam interface (submitOrder throws IOException)
│   │   ├── OrderManagementSystemImpl.java Dummy OMS — the displaced impl, excluded from coverage
│   │   ├── OmsOrderRequest.java          @JukeIgnorable confirmationNumber
│   │   ├── OmsReceipt.java               Replayed deterministically (omsOrderId)
│   │   ├── OrderResult.java              COMPLETED / RECORDED outcome returned to the SPA
│   │   ├── ApiOrderRequest.java          POST body {sku, quantity, confirmationNumber}
│   │   ├── Catalog.java / Product.java   The (tiny) product list
│   ├── resources/application.yml         juke.enabled + juke.coverage.threshold.*
│   └── web-app/                          Vite + React SPA
│       └── src/
│           ├── App.jsx                   Layout + "View test coverage" button
│           ├── OrderApp.jsx              Buy → 3 orders, popups, order log
│           ├── CoverageModal.jsx         Coverage popup: summary + report tabs
│           └── AboutPanel.jsx            Reached only via the footer link (keeps UI cov < 100%)
└── src/test/playwright/e2e/
    ├── order-flow.spec.ts                The four-run journey + coverage popup
    ├── coverage-fixture.ts               Harvests window.__coverage__
    └── global-teardown.ts                nyc report → ~/juke-demo/coverage/ui
```

---

## Juke features this sample exercises

- **`@Juke("juke")` on the seam** — records under global record mode (no cookie) and replays
  per-session when a `JUKE_SESSION` cookie is present. One annotation covers both.
- **Per-session replay + reports** — each replay run is `session/start?track=order-demo&description=…`
  → drive → `session/stop`; `GET /service/recording/report?track=order-demo` shows one session per run.
- **`@JukeIgnorable`** — the changing confirmation number is skipped in the replay input-diff, so
  the report reads `COMPLETED` (not `COMPLETED_WITH_DEVIATIONS`).
- **Remix fault injection** — `delaySchedule` (10s) and `exceptionSchedule` (`IOException`) on the
  2nd order, with `GET /service/remix/clear` between runs so faults don't compound.
- **Functional coverage** — `/service/coverage` (server JaCoCo + UI nyc), with the OMS impl
  auto-excluded as a `@Juke` seam.

On a full four-run journey, server line coverage lands around **70%** and UI lines around **90%**;
the coverage popup's badge turns green once both halves cross their thresholds.

> Why not 100%? `OrderService.cancelOrder` / `reconcile` (admin ops) and `AboutPanel.jsx` (footer
> link) are deliberately never exercised by the journey, so the gate stays meaningful.

---

## Troubleshooting

- **`record/start` / remix returns "Unavailable Service" (500):** the server got stuck in a
  passthrough state. Restart it; a fresh start is in `IGNORE` mode and all control endpoints work.
- **Coverage popup's UI half is empty:** you built without `-Pcoverage`, or no Playwright run has
  produced the nyc report yet. Rebuild with `-Pcoverage` and run `demo-run-playwright`.
- **Rebuilt the framework but the change isn't taking effect:** `spring-boot:repackage` is
  incremental — run `mvn clean package` (not bare `package`) so the fat jar re-embeds the updated
  dependency. See [`../LESSONS_LEARNED.md`](../LESSONS_LEARNED.md).

---

## Related reading

- [`../LESSONS_LEARNED.md`](../LESSONS_LEARNED.md) — authoring notes distilled from building this sample
- [`juke-sample-coverage`](../juke-sample-coverage/README.md) — the live coverage-dashboard sample
- [`juke-sample-session`](../juke-sample-session/README.md) — cookie-based per-session replay
- [`COMMUNITY_GUIDE.md`](../../COMMUNITY_GUIDE.md) — Remix (fault injection) and coverage chapters
