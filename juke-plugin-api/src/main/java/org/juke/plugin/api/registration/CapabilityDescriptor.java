package org.juke.plugin.api.registration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.juke.plugin.api.PluginCapability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-capability descriptor sent in {@link PluginRegistration#capabilities}. Lets a plugin tell
 * remix:
 *
 * <ul>
 *   <li>which {@link PluginCapability} it implements,</li>
 *   <li>extra static metadata for the admin UI ({@link #displayMetadata}, e.g. {@code
 *       harnessId=playwright}),</li>
 *   <li>an optional JSON-Schema fragment for the configure form ({@link #configSchema}).</li>
 * </ul>
 *
 * <p>The shape is deliberately flexible — admin-ui renders generic controls keyed off
 * {@link PluginCapability} and uses {@code displayMetadata} to populate them, so adding a new
 * descriptor field rarely requires both ends to ship in lockstep.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CapabilityDescriptor {

    private PluginCapability capability;

    /**
     * Free-form metadata surfaced to admin-ui. For {@code UI_HARNESS} this is conventionally
     * {@code {"harnessId": "playwright", "browserModes": ["HEADLESS", "HEADED"]}}; for
     * {@code RECORDING_TRANSFORMER} it might describe redaction strategies. Map kept open so
     * descriptor schemas can evolve without bumping the plugin protocol.
     */
    private Map<String, Object> displayMetadata = new LinkedHashMap<>();

    /**
     * Optional JSON-Schema fragment driving the {@code POST /service/plugins/{id}/configure}
     * form. {@code null} means "this capability has no per-plugin config UI".
     */
    private Map<String, Object> configSchema;

    public CapabilityDescriptor() {}

    public CapabilityDescriptor(PluginCapability capability) {
        this.capability = capability;
    }

    public PluginCapability getCapability() {
        return capability;
    }

    public void setCapability(PluginCapability capability) {
        this.capability = capability;
    }

    public Map<String, Object> getDisplayMetadata() {
        return displayMetadata == null ? Collections.emptyMap() : displayMetadata;
    }

    public void setDisplayMetadata(Map<String, Object> displayMetadata) {
        this.displayMetadata = displayMetadata == null ? new LinkedHashMap<>() : displayMetadata;
    }

    public Map<String, Object> getConfigSchema() {
        return configSchema;
    }

    public void setConfigSchema(Map<String, Object> configSchema) {
        this.configSchema = configSchema;
    }
}
