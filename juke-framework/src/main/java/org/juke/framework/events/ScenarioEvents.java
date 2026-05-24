package org.juke.framework.events;

/**
 * Static service-locator for the framework→scenario decoupling primitives
 * introduced in Phase 3.
 *
 * <p>Both the {@link ScenarioEventSink} and the {@link IgnoreRuleProvider}
 * default to no-op/empty implementations. The scenario service registers
 * concrete beans on application startup (see {@code FrameworkSinkAdapter}
 * and {@code FrameworkIgnoreRuleProviderAdapter}). This keeps
 * {@code juke-framework} free of any compile-time dependency on the
 * scenario persistence module while still letting the framework call into
 * persistence from inside the proxy / AOP advice.
 */
public final class ScenarioEvents {

    private static volatile ScenarioEventSink sink = ScenarioEventSink.NO_OP;
    private static volatile IgnoreRuleProvider ignoreRuleProvider = IgnoreRuleProvider.EMPTY;

    private ScenarioEvents() {}

    public static ScenarioEventSink sink() {
        return sink;
    }

    public static void setSink(ScenarioEventSink newSink) {
        sink = newSink != null ? newSink : ScenarioEventSink.NO_OP;
    }

    public static IgnoreRuleProvider ignoreRuleProvider() {
        return ignoreRuleProvider;
    }

    public static void setIgnoreRuleProvider(IgnoreRuleProvider provider) {
        ignoreRuleProvider = provider != null ? provider : IgnoreRuleProvider.EMPTY;
    }

    /** Reset to the no-op defaults. Useful in test teardown. */
    public static void reset() {
        sink = ScenarioEventSink.NO_OP;
        ignoreRuleProvider = IgnoreRuleProvider.EMPTY;
    }
}
