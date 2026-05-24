package org.juke.plugin.api.capability.scaffold;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code POST /plugin/v1/scaffold/generate} request — remix asks a {@code SCAFFOLD_GENERATION}
 * plugin to turn an accepted suggestion into an executable Playwright spec body. The plugin
 * returns the spec text in {@link ScaffoldResult#getSpecBody}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScaffoldRequest {

    private String suggestionId;
    private String useCaseName;
    private String useCaseDescription;
    private Map<String, Object> suggestionMetadata = new LinkedHashMap<>();
    private Map<String, Object> pluginConfig = new LinkedHashMap<>();

    public ScaffoldRequest() {}

    public String getSuggestionId() { return suggestionId; }
    public void setSuggestionId(String suggestionId) { this.suggestionId = suggestionId; }
    public String getUseCaseName() { return useCaseName; }
    public void setUseCaseName(String useCaseName) { this.useCaseName = useCaseName; }
    public String getUseCaseDescription() { return useCaseDescription; }
    public void setUseCaseDescription(String useCaseDescription) { this.useCaseDescription = useCaseDescription; }

    public Map<String, Object> getSuggestionMetadata() {
        return suggestionMetadata == null ? Collections.emptyMap() : suggestionMetadata;
    }
    public void setSuggestionMetadata(Map<String, Object> suggestionMetadata) {
        this.suggestionMetadata = suggestionMetadata == null ? new LinkedHashMap<>() : suggestionMetadata;
    }
    public Map<String, Object> getPluginConfig() {
        return pluginConfig == null ? Collections.emptyMap() : pluginConfig;
    }
    public void setPluginConfig(Map<String, Object> pluginConfig) {
        this.pluginConfig = pluginConfig == null ? new LinkedHashMap<>() : pluginConfig;
    }
}
