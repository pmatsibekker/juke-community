# juke-sample-plugin

Reference implementation for the **plugin SDK** — the Apache-2.0 contract
(`juke-plugin-api` + `juke-plugin-sdk`) that lets third parties extend Juke
without forking.

## What it validates

`DemoRecordingTransformer` is a `RECORDING_TRANSFORMER` capability built on
`juke-plugin-sdk`:

```java
@PluginCapability(org.juke.plugin.api.PluginCapability.RECORDING_TRANSFORMER)
public class DemoRecordingTransformer {
    @PluginEndpoint("before-write") public TransformResult beforeWrite(TransformRequest r) { ... }
    @PluginEndpoint("after-read")   public TransformResult afterRead(TransformRequest r)   { ... }
}
```

On `ApplicationReadyEvent` the SDK discovers every `@PluginCapability` bean and
POSTs a registration to the agent's `/service/plugins/register`. The agent then
lists the plugin (and its capabilities) at `GET /service/plugins`.

> For a self-contained demo this **one process is both the agent and the
> plugin** — `juke.plugin.remix-base-url` points at itself. In production the
> plugin is a separate service pointed at a remote agent.

## How it's verified

- **`PluginRegistrationTest`** (`mvn test`) — boots the app and polls
  `GET /service/plugins` until `demo-transformer` appears, asserting it
  advertises `RECORDING_TRANSFORMER`. Headless, unit-testable gate.
- **Visible UI** (`src/main/resources/static/index.html`, plain HTML — no npm) —
  a banner explains the SDK relay; the page reads `/service/plugins` and lists
  the registered plugin, its status, and capabilities.

## Run it

```bash
mvn -o -pl juke-samples/juke-sample-plugin -am test
# or boot it and open http://localhost:8080
mvn -pl juke-samples/juke-sample-plugin spring-boot:run
```
