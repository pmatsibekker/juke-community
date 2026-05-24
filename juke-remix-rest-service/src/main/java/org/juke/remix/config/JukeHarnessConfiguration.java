package org.juke.remix.config;

import org.juke.framework.harness.NoOpUiHarness;
import org.juke.framework.harness.UiHarness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Wires the active {@link UiHarness} as {@code @Primary} based on the {@code juke.ui-harness}
 * property (Phase 2 / D15).
 *
 * <p>Behaviour:
 * <ul>
 *   <li>{@code juke.ui-harness} unset or {@code "none"} → {@link NoOpUiHarness} is primary.</li>
 *   <li>{@code juke.ui-harness=playwright} (or any other id) → the bean whose
 *       {@link UiHarness#id()} matches becomes primary.</li>
 *   <li>Named harness not found on the classpath → fail-fast at startup with a clear message
 *       listing the harnesses that <em>are</em> available.</li>
 * </ul>
 *
 * <p>Note: Spring's {@code @Primary} is a class-level annotation and cannot be applied dynamically
 * to a bean instance, so we expose the elected harness as a separate {@code @Primary} bean named
 * {@code activeUiHarness}. Consumers that need the elected harness inject {@code UiHarness}
 * directly (Spring picks the {@code @Primary}); consumers that want to enumerate all harnesses
 * inject {@code List<UiHarness>}.
 *
 * <h3>KI-2 fix — broken constructor cycle</h3>
 * <p>This class previously took {@code List<UiHarness>} via constructor. That triggered an
 * unresolvable cycle at boot involving {@code jukeCookieFilter} →
 * {@code SessionRegistry} → {@code JukeHarnessConfiguration} construction →
 * {@code List<UiHarness>} resolution → {@link #activeUiHarness} → instance currently in creation
 * → cycle. The fix moves {@code List<UiHarness>} off the constructor onto
 * {@link #activeUiHarness}'s method parameter; the harness-discovery log was inlined into
 * {@code activeUiHarness} itself so we no longer need a {@code @PostConstruct} that would
 * re-trigger eager UiHarness resolution while the configuration bean is still being initialised.
 *
 * <h3>Phase 5.B — bundle-backed session registry split out</h3>
 * <p>The {@code SessionRegistry} bean that swapped in {@code BundleBackedJukeSessionRegistry}
 * lived here until §5.B. With the bundle resolver and registry classes relocated to
 * {@code juke-scenario-service} as part of the JPA module move, the bean wiring moves with
 * them ({@code org.juke.scenario.session.BundleSessionConfiguration}). This class is now
 * Community-clean: pure framework deps, no scenario-service references.
 */
@Configuration
public class JukeHarnessConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(JukeHarnessConfiguration.class);

    @Value("${juke.ui-harness:none}")
    private String configuredHarnessId;

    @Bean
    @Primary
    public UiHarness activeUiHarness(List<UiHarness> discoveredHarnesses) {
        // Log the discovered harnesses here (was previously a @PostConstruct, but that ran
        // while this bean was still in creation and re-triggered the same cycle KI-2 fixes).
        String discovered = discoveredHarnesses.stream()
                .map(h -> h.id() + " (" + h.getClass().getSimpleName() + ")")
                .collect(Collectors.joining(", "));
        LOG.info("Discovered {} UiHarness implementation(s): [{}]",
                discoveredHarnesses.size(), discovered);

        Map<String, UiHarness> byId = discoveredHarnesses.stream()
                .collect(Collectors.toMap(UiHarness::id, h -> h, (a, b) -> {
                    throw new IllegalStateException(
                            "Two UiHarness beans share the same id '" + a.id() + "': " +
                                    a.getClass().getName() + " and " + b.getClass().getName());
                }));

        String requested = (configuredHarnessId == null || configuredHarnessId.isBlank())
                ? NoOpUiHarness.ID
                : configuredHarnessId.trim().toLowerCase();

        UiHarness elected = byId.get(requested);
        if (elected == null) {
            String available = String.join(", ", byId.keySet());
            throw new IllegalStateException(
                    "juke.ui-harness=" + requested + " but no UiHarness bean with that id is on " +
                            "the classpath. Available harnesses: [" + available + "]. Either set " +
                            "juke.ui-harness=none to run headless, or add the appropriate " +
                            "harness module (e.g. juke-plugin-playwright-harness) to your build.");
        }

        LOG.info("Active Juke UI harness: id='{}' impl={} canExecute={}",
                elected.id(), elected.getClass().getSimpleName(), elected.canExecuteRecording());
        return elected;
    }
}
