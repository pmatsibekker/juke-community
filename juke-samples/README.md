# Juke Samples

Reference implementations for the Juke framework. Each sub-module is a
self-contained Spring Boot application demonstrating one concept in isolation;
they exist to show how to wire Juke into a real app, not to serve as a
production-grade test corpus. Examples are not held to a coverage SLA.

> **First time?** Read **[DEMO.md](./DEMO.md)** — a 15-minute, fully
> copy-pasteable walk-through that records a deterministic ZIP, replays
> it, shows Juke catching a tampered input, and demonstrates how to
> silence false positives (an auto-incremented `id` field). Includes a
> Playwright spec you can run with `--ui` to watch the whole thing in a
> visible Chrome window.

| Module | What it demonstrates | Run |
|---|---|---|
| [`juke-sample-greeting`](./juke-sample-greeting) | Basic record/replay against a Spring REST controller via `@Juke`-annotated DAO field, with an integrated React UI bundled into the Spring Boot jar | `mvn -pl juke-samples/juke-sample-greeting spring-boot:run` |
| [`juke-sample-session`](./juke-sample-session) | Cookie-bound per-session replay (`SessionAwareReplayHandler`) — concurrent Playwright workers each get their own track without restarting the JVM. Playwright specs included | `mvn -pl juke-samples/juke-sample-session spring-boot:run` |
| [`juke-sample-todo`](./juke-sample-todo) | REST CRUD wired through a `@Juke` DAO; covers POST / PUT / DELETE record semantics | `mvn -pl juke-samples/juke-sample-todo spring-boot:run` |
| [`juke-sample-annotations`](./juke-sample-annotations) | Field-, method-, and constructor-level `@Juke` patterns plus multi-service composition. Reference code, no controllers | `mvn -pl juke-samples/juke-sample-annotations spring-boot:run` |
| [`juke-sample-coverage`](./juke-sample-coverage) | End-to-end demo of `juke-coverage` — a small user journey on the left of the page, a live coverage dashboard on the right that updates as you click through. Both server (JaCoCo) and UI (nyc/Istanbul) halves visible | See [`juke-sample-coverage/README.md`](./juke-sample-coverage/README.md) — needs the `-Pcoverage` build and the bundled launch scripts |
| [`juke-sample-exceptions`](./juke-sample-exceptions) | Exception/latency flows: a SKU order places 3 orders through a `@Juke` OMS seam, driven four times — record → deterministic replay → replay with an injected delay ("queued") → replay with an injected exception ("technical difficulties"). Random confirmation numbers are `@JukeIgnorable`; coverage is shown in a separate popup | See [`juke-sample-exceptions/README.md`](./juke-sample-exceptions/README.md) — needs the `-Pcoverage` build and the bundled launch scripts |

## Common setup

Every sample includes a default `application.yml` with `juke.enabled: true` and
a `juke.path` pointing at `${user.home}/juke/<sample-name>`. Activate
record / replay through the REST API once the app is running — there are no
profile switches to remember:

```bash
# Begin recording
curl 'http://localhost:8080/service/record/start?track=demo'

# Drive traffic against whatever endpoints the sample exposes
curl 'http://localhost:8080/greeting?name=Alice'

# Save the recording
curl 'http://localhost:8080/service/record/end' -o /tmp/demo.zip

# Replay it for one isolated session
curl -c /tmp/cookies.txt 'http://localhost:8080/service/session/start?track=demo'
curl -b /tmp/cookies.txt 'http://localhost:8080/greeting?name=anyone'
# Returns "Hello, Alice!" — the first recorded response
```

See [`COMMUNITY_GUIDE.md`](../COMMUNITY_GUIDE.md) for the full reference.

## Why split the samples?

The previous mono-sample (`juke-sample-rest-service`) mixed four unrelated demo
patterns under one component scan, so newcomers had to read the entire codebase
to understand any single capability. Splitting into focused modules means:

- **Build / run / test in isolation** — you can iterate on one example without
  rebuilding the others
- **No accidental coupling** — each module declares only the dependencies its
  example actually needs
- **Clearer narrative** — each `README.md` (where present) explains one thing
- **Independent UIs** — the `juke-sample-greeting` module bundles its own React
  app via the Spring Boot jar; other samples can do the same as their stories
  warrant
