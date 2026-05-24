package org.juke.plugin.api.paths;

import org.juke.plugin.api.PluginCapability;

/**
 * Canonical paths from §8.8 — the URLs remix uses when it calls <em>into</em> a plugin. Kept in
 * one place so plugin authors and dispatcher code agree on the contract without duplicating
 * string literals.
 *
 * <p>The {@code /plugin/v1/} prefix is the version namespace: a future v2 of a capability path
 * (e.g. a new RECORDING_TRANSFORMER signature) would coexist under {@code /plugin/v2/...}
 * without breaking existing plugins.
 */
public final class PluginPaths {

    public static final String PLUGIN_BASE = "/plugin/v1";

    /** {@code POST /plugin/v1/use-case-suggestion/analyze} */
    public static final String USE_CASE_SUGGESTION_ANALYZE =
            PLUGIN_BASE + "/use-case-suggestion/analyze";

    /** {@code POST /plugin/v1/scaffold/generate} */
    public static final String SCAFFOLD_GENERATE = PLUGIN_BASE + "/scaffold/generate";

    /** {@code POST /plugin/v1/transform/before-write} */
    public static final String TRANSFORM_BEFORE_WRITE = PLUGIN_BASE + "/transform/before-write";

    /** {@code POST /plugin/v1/transform/after-read} */
    public static final String TRANSFORM_AFTER_READ = PLUGIN_BASE + "/transform/after-read";

    /** {@code POST /plugin/v1/ui-harness/session-start} */
    public static final String UI_HARNESS_SESSION_START = PLUGIN_BASE + "/ui-harness/session-start";

    /** {@code POST /plugin/v1/ui-harness/session-stop} */
    public static final String UI_HARNESS_SESSION_STOP = PLUGIN_BASE + "/ui-harness/session-stop";

    /**
     * Configuration sink — admin-ui forwards JSON here via remix's
     * {@code POST /service/plugins/{id}/configure}. Lives outside {@code /v1} because configure
     * is a control-plane concern, not a capability call.
     */
    public static final String CONFIGURE = "/plugin/configure";

    private PluginPaths() {}

    /**
     * Resolve the canonical endpoint for a single-endpoint capability (everything except
     * {@code RECORDING_TRANSFORMER}, which has two endpoints, and {@code UI_HARNESS}, which
     * also has two — callers there must pick {@code session-start} or {@code session-stop}
     * explicitly).
     *
     * @throws IllegalArgumentException for capabilities that don't have a single canonical path
     */
    public static String singleEndpointFor(PluginCapability capability) {
        return switch (capability) {
            case USE_CASE_SUGGESTION -> USE_CASE_SUGGESTION_ANALYZE;
            case SCAFFOLD_GENERATION -> SCAFFOLD_GENERATE;
            case RECORDING_TRANSFORMER, UI_HARNESS, ASSERTION_GENERATION ->
                    throw new IllegalArgumentException(
                            capability + " has multiple endpoints — pick explicitly");
        };
    }
}
