package org.juke.plugin.api;

/**
 * Capability category v1, per §5.2 of the Juke Scenario Testing Plan. A plugin declares the
 * capabilities it implements at registration time; remix uses the capability to decide which
 * canonical {@code /plugin/v1/...} sub-path to call.
 *
 * <p>Adding a new capability is intentionally rare and requires a coordinated change to
 * admin-ui (which renders capability-specific controls). Within a category, however, plugins
 * are interchangeable: adding a second {@code USE_CASE_SUGGESTION} plugin requires zero
 * admin-ui changes.
 *
 * <p>The enum constants are uppercase strings rather than ordinals on the wire — Jackson's
 * default enum serialization writes the {@link Enum#name()}, which is stable across plan
 * revisions. {@code ASSERTION_GENERATION} is reserved for the v2 work in §14 and must be kept
 * here so existing plugin builds compile against the next version that activates it.
 */
public enum PluginCapability {

    UI_HARNESS,
    USE_CASE_SUGGESTION,
    SCAFFOLD_GENERATION,
    RECORDING_TRANSFORMER,

    /** Reserved — see §14 (assertion synthesis). Not yet wired through the dispatcher. */
    ASSERTION_GENERATION
}
