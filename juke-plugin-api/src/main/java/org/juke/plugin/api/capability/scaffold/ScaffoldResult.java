package org.juke.plugin.api.capability.scaffold;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Response body from {@code POST /plugin/v1/scaffold/generate}. {@link #specBody} is the raw
 * spec source the plugin generated; {@link #fileName} is the suggested filename the admin UI
 * proposes when offering "save scaffold to disk". {@link #warnings} surfaces non-fatal notes
 * (e.g. "model fell back to a generic template").
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScaffoldResult {

    private String fileName;
    private String specBody;
    private List<String> warnings = new ArrayList<>();

    public ScaffoldResult() {}

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getSpecBody() { return specBody; }
    public void setSpecBody(String specBody) { this.specBody = specBody; }
    public List<String> getWarnings() {
        return warnings == null ? Collections.emptyList() : warnings;
    }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
}
