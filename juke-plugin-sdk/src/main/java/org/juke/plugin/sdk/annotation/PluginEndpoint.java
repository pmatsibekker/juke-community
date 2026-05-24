package org.juke.plugin.sdk.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for the methods on a capability bean that handle a specific {@code
 * /plugin/v1/...} sub-path. Used together with {@link PluginCapability} on the enclosing class.
 *
 * <p>Example:
 * <pre>{@code
 *   @PluginCapability(UI_HARNESS)
 *   public class PlaywrightCapability {
 *       @PluginEndpoint("session-start")
 *       public UiHarnessSessionStartResponse start(UiHarnessSessionStartRequest req) { ... }
 *
 *       @PluginEndpoint("session-stop")
 *       public UiHarnessSessionStopResponse stop(UiHarnessSessionStopRequest req) { ... }
 *   }
 * }</pre>
 *
 * <p>Phase 4.7 only carries the metadata — the SDK's auto-configured controller in a later
 * phase reads the annotations to dispatch incoming HTTP into the right method. For now plugin
 * authors writing controllers by hand can already use the annotations as documentation, and
 * the {@link org.juke.plugin.sdk.lifecycle.PluginRegistrationLifecycle} reads them to build the
 * capability list it sends in the registration payload.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PluginEndpoint {

    /**
     * Endpoint key — the sub-path after the capability prefix. For {@code UI_HARNESS} the legal
     * keys are {@code "session-start"} and {@code "session-stop"}; for
     * {@code RECORDING_TRANSFORMER} they are {@code "before-write"} and {@code "after-read"};
     * for the others, the convention is the single-endpoint name (e.g. {@code "analyze"}).
     */
    String value();
}
