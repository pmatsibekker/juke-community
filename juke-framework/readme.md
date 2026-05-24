# juke-framework

The engine of the Juke record-and-replay system. This module provides everything needed to intercept Spring beans, record their upstream interactions to a portable ZIP archive, and replay those recordings deterministically — with no hand-written mocks and no upstream availability required.

Add `juke-remix-rest-service` alongside it to get the `/service/*` HTTP control surface. Add the optional `juke-coverage` module for functional test coverage.

---

## What it provides

- **Annotation surface** — `@Juke`, `@JukeController`, `@JukeIgnorable`
- **Proxy infrastructure** — JDK dynamic proxies for interfaces, CGLIB subclass proxies for concrete classes and concrete fields (e.g. Spring `*Template` beans), with method-level interceptors
- **Storage SPI** — `JukeStorage` and its default ZIP-on-disk implementation (`JukeZipDAOImpl`)
- **Session registry** — `JukeSessionRegistry`, tracking active cookie-scoped replay sessions
- **Mock registry** — `JukeMockRegistry`, recording every implementation displaced by a proxy (used by `juke-coverage` to exclude `@Juke`-mocked classes from the coverage figure)
- **Spring Boot auto-configuration** — everything wires itself when `juke.enabled: true`; zero footprint when the property is absent

---

## Quick start

### 1. Add the dependency

```xml
<dependency>
    <groupId>org.juke.harnesss</groupId>
    <artifactId>juke-framework</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 2. Open the component scan

```java
@SpringBootApplication
@ComponentScan(basePackages = {"org.juke.framework", "org.juke.remix", "com.yourcompany"})
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

### 3. Enable Juke per environment

```yaml
# application-dev.yml
juke:
  enabled: true        # master toggle — keep absent in application-prod.yml
  path: /tmp/juke      # where recording ZIPs are written and read
```

### 4. Annotate one collaborator

```java
@Service
public class CheckoutService {

    @Juke
    private InventoryClient inventory;   // this field is now intercepted

    public Order place(String sku, int qty) {
        return inventory.reserve(sku, qty);
    }
}
```

Add `juke-remix-rest-service` and drive record/replay over REST — no restarts needed.

---

## Annotations

### `@Juke`

The workhorse. Targets: `TYPE`, `FIELD`, `METHOD`, `PARAMETER`. Retention: `RUNTIME`.

**On a field — JDK dynamic proxy** (interface types):

```java
@Service
public class OrderService {
    @Juke private InventoryClient inventory;
    @Juke private PricingClient   pricing;
}
```

**On a class — CGLIB subclass proxy** (concrete types; every public method intercepted):

```java
@Juke
@Service
public class FulfillmentService implements Shippable, Trackable { ... }
```

**On a concrete field — CGLIB subclass** (e.g. a Spring `RestTemplate` you don't own): the bean is wrapped in a subclass that delegates to the real instance, so it stays assignable to the field. Use `name` to set the recording-identity prefix (disambiguating two beans of the same type) and `excludeMethods` to skip config/builder calls:

```java
@Autowired
@Juke(name = "shipping", excludeMethods = {"setRequestFactory", "setMessageConverters"})
private RestTemplate restTemplate;
```

A `final` concrete class can't be subclassed — type the field as an interface it implements instead.

The optional `value()` attribute pins the annotation to a specific mode regardless of the global setting:

```java
@Juke("ignore")
private DiagnosticsClient diagnostics;   // always pass-through, never recorded
```

Accepted values: `juke` (default — follow the global mode), `record`, `replay`, `ignore`, `none`, `disable`.

---

### `@JukeController`

Marks a Spring `@RestController` for controller-level AOP advice — request/response MDC tagging and cookie-bound session resolution. Rarely applied directly; used when wiring the bundle-backed session flow.

---

### `@JukeIgnorable`

Opts a single method or field out of a class-level `@Juke`:

```java
@Juke
@Service
public class OrderService {
    public Order place(String sku) { ... }   // intercepted

    @JukeIgnorable
    public void warmCache() { ... }           // never recorded or replayed
}
```

`IgnoreStrategy` controls field-comparison behaviour on replay:

| Strategy | Behaviour |
|---|---|
| `ALWAYS` *(default)* | Skip this field in every comparison — right for generated IDs and UUIDs. |
| `NOT_NULL` | Skip the value diff, but flag null-vs-non-null mismatches — right for timestamps. |

---

## Storage SPI

`JukeStorage` is the framework's storage abstraction, with two levels:

- **Per-recording** — read/write entries within a single ZIP (`readFromFile`, `writeToFile`, `getFileNames`, `path`)
- **Recording-store** — enumerate, load, and save whole recordings by name (`listRecordings`, `loadRecording`, `saveRecording`)

The default implementation — `JukeZipDAOImpl` — treats `juke.storage.folder.path` as the recording store and each `*.zip` file as a per-recording handle. It is registered `@ConditionalOnMissingBean(JukeStorage.class)`, so exposing your own `JukeStorage` bean silently replaces it.

The Enterprise `juke-scenario-service` module ships a JPA-backed alternative activated with `juke.storage.backend=db`.

---

## Configuration keys

| Key | Default | Notes |
|---|---|---|
| `juke.enabled` | `false` | Master toggle — nothing activates when absent or false. |
| `juke.path` | `${java.io.tmpdir}/juke` | Filesystem directory for recording ZIPs. |
| `juke.mode` | `ignore` | Global mode — usually flipped at runtime via REST. |
| `juke.args-validation` | `warn` | `warn` / `strict` / `off` — argument comparison on replay. |
| `juke.storage.folder.path` | `recordings` | Root directory for the storage SPI. |

See [`COMMUNITY_GUIDE.md §5`](../COMMUNITY_GUIDE.md) for the full key reference including `juke.coverage.*`.

---

## Related modules

| Module | Role |
|---|---|
| `juke-remix-rest-service` | Adds `/service/*` HTTP endpoints — required to drive record/replay/sessions over REST. |
| `juke-coverage` | Optional. Functional test coverage via JaCoCo (server) and nyc/Istanbul (UI). |
| `juke-plugin-api` | DTOs for the plugin contract. |
| `juke-plugin-sdk` | Spring Boot starter for writing Juke plugins. |

---

## Further reading

- [`COMMUNITY_GUIDE.md`](../COMMUNITY_GUIDE.md) — full reference for annotations, modes, sessions, Remix, coverage, and configuration
- [`JUKE_IN_A_NUTSHELL.md`](../JUKE_IN_A_NUTSHELL.md) — O'Reilly-style desk reference
- [`juke-coverage/README.md`](../juke-coverage/README.md) — functional test coverage setup and API reference
