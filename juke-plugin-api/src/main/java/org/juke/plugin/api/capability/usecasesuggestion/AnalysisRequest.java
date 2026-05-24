package org.juke.plugin.api.capability.usecasesuggestion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code POST /plugin/v1/use-case-suggestion/analyze} request — remix asks a {@code
 * USE_CASE_SUGGESTION} plugin to scan a workspace and suggest use cases the user might want to
 * record.
 *
 * <p>The shape is intentionally thin: the plugin is expected to read the codebase via
 * {@link #workspaceRoot} (the path is local to the plugin process, since plugins typically run
 * alongside the workspace they analyse). {@link #pluginConfig} is the per-plugin JSON blob set
 * via {@code POST /service/plugins/{id}/configure}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisRequest {

    private String workspaceRoot;
    private List<String> includeGlobs;
    private List<String> excludeGlobs;
    private Map<String, Object> pluginConfig = new LinkedHashMap<>();

    public AnalysisRequest() {}

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public List<String> getIncludeGlobs() {
        return includeGlobs == null ? Collections.emptyList() : includeGlobs;
    }

    public void setIncludeGlobs(List<String> includeGlobs) {
        this.includeGlobs = includeGlobs;
    }

    public List<String> getExcludeGlobs() {
        return excludeGlobs == null ? Collections.emptyList() : excludeGlobs;
    }

    public void setExcludeGlobs(List<String> excludeGlobs) {
        this.excludeGlobs = excludeGlobs;
    }

    public Map<String, Object> getPluginConfig() {
        return pluginConfig == null ? Collections.emptyMap() : pluginConfig;
    }

    public void setPluginConfig(Map<String, Object> pluginConfig) {
        this.pluginConfig = pluginConfig == null ? new LinkedHashMap<>() : pluginConfig;
    }
}
