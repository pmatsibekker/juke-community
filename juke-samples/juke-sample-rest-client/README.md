# juke-sample-rest-client

Reference implementation for **concrete-field `@Juke`** — wrapping a real
`RestTemplate` (not an interface) for record & replay. This is the case the
consolidated `@Juke(name=…, excludeMethods=…)` attributes exist for.

## What it validates

Two same-typed `RestTemplate` seams call a co-located stub upstream:

```java
@Juke(name = "shipping", excludeMethods = {"headForHeaders"})
@Autowired @Qualifier("shippingRestTemplate")
RestTemplate restTemplate;          // ShippingClient

@Juke(name = "pricing")
@Autowired @Qualifier("pricingRestTemplate")
RestTemplate restTemplate;          // PricingClient
```

- **Concrete-field wrapping** — Juke wraps each `RestTemplate` in a CGLIB
  subclass that delegates to the real bean; `getForObject` is recorded and
  replayed.
- **`name` disambiguation** — two seams of the same type would both key as
  `RestTemplate.*` and collide; `name` keys them `shipping.*` / `pricing.*`.
- **`excludeMethods`** — the shipping seam's `headForHeaders` health check is
  never recorded.
- **Replay returns the recorded value** — the upstream stamps a fresh random
  `quoteId` on every live call, so a replayed `quoteId` (matching the recorded
  one) proves the seam replayed rather than hitting the upstream.

> The concrete-field path follows the **global** mode (it is not cookie-session
> aware — that is the Phase-2 enhancement), so this sample drives **global**
> record → replay via `/service/record/*` and `/service/replay/*`, not sessions.

## How it's verified

- **`RestClientRecordReplayTest`** (`mvn test`) — drives record → replay through
  the real `/service/*` control surface and asserts replayed `quoteId`s,
  `shipping.`/`pricing.` recording keys, and the absence of `headForHeaders`.
  This is the headless, unit-testable gate.
- **Visible UI** (`src/main/resources/static/index.html`, plain HTML — no npm) —
  a banner explains what's being validated, and the steps (Record → Replay →
  Show recording keys) show live vs. replayed `quoteId`s and the recording keys.

## Run it

```bash
# Build + run the verification gate
mvn -o -pl juke-samples/juke-sample-rest-client -am test

# Or boot it and open the UI at http://localhost:8080
mvn -pl juke-samples/juke-sample-rest-client spring-boot:run
#   1. Record (live)        → records both seams under the active track
#   2. Replay (upstream off) → returns the recorded quoteIds
#   Show recording keys      → shipping.getForObject.1, pricing.getForObject.1
```
