package org.juke.plugin.api;

import org.juke.plugin.api.paths.PluginPaths;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The capability names and the canonical paths are part of the wire protocol — pin them so a
 * future rename gets caught here rather than after a plugin has shipped against the old names.
 */
class PluginCapabilityNamesTest {

    @Test
    void enumNamesMatchProtocolStrings() {
        assertThat(PluginCapability.UI_HARNESS.name()).isEqualTo("UI_HARNESS");
        assertThat(PluginCapability.USE_CASE_SUGGESTION.name()).isEqualTo("USE_CASE_SUGGESTION");
        assertThat(PluginCapability.SCAFFOLD_GENERATION.name()).isEqualTo("SCAFFOLD_GENERATION");
        assertThat(PluginCapability.RECORDING_TRANSFORMER.name()).isEqualTo("RECORDING_TRANSFORMER");
        assertThat(PluginCapability.ASSERTION_GENERATION.name()).isEqualTo("ASSERTION_GENERATION");
    }

    @Test
    void canonicalPathsMatchPlanSection_8_8() {
        assertThat(PluginPaths.USE_CASE_SUGGESTION_ANALYZE).isEqualTo("/plugin/v1/use-case-suggestion/analyze");
        assertThat(PluginPaths.SCAFFOLD_GENERATE).isEqualTo("/plugin/v1/scaffold/generate");
        assertThat(PluginPaths.TRANSFORM_BEFORE_WRITE).isEqualTo("/plugin/v1/transform/before-write");
        assertThat(PluginPaths.TRANSFORM_AFTER_READ).isEqualTo("/plugin/v1/transform/after-read");
        assertThat(PluginPaths.UI_HARNESS_SESSION_START).isEqualTo("/plugin/v1/ui-harness/session-start");
        assertThat(PluginPaths.UI_HARNESS_SESSION_STOP).isEqualTo("/plugin/v1/ui-harness/session-stop");
        assertThat(PluginPaths.CONFIGURE).isEqualTo("/plugin/configure");
    }

    @Test
    void singleEndpointResolverRejectsMultiEndpointCapabilities() {
        assertThat(PluginPaths.singleEndpointFor(PluginCapability.USE_CASE_SUGGESTION))
                .isEqualTo(PluginPaths.USE_CASE_SUGGESTION_ANALYZE);
        assertThat(PluginPaths.singleEndpointFor(PluginCapability.SCAFFOLD_GENERATION))
                .isEqualTo(PluginPaths.SCAFFOLD_GENERATE);
        assertThatThrownBy(() -> PluginPaths.singleEndpointFor(PluginCapability.UI_HARNESS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PluginPaths.singleEndpointFor(PluginCapability.RECORDING_TRANSFORMER))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
