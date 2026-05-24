package org.juke.plugin.api.capability.usecasesuggestion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Response body from {@code POST /plugin/v1/use-case-suggestion/analyze}. {@link #suggestions}
 * is the list admin-ui surfaces; each entry carries enough metadata for the user to accept,
 * reject, or refine a suggestion before it becomes a recorded use case.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SuggestionResult {

    public static class Suggestion {
        private String suggestionId;
        private String title;
        private String description;
        private List<String> evidenceFiles = new ArrayList<>();
        /** Free-form per-suggestion metadata the plugin wants to round-trip back to itself. */
        private Map<String, Object> metadata = new LinkedHashMap<>();

        public Suggestion() {}

        public String getSuggestionId() { return suggestionId; }
        public void setSuggestionId(String suggestionId) { this.suggestionId = suggestionId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getEvidenceFiles() {
            return evidenceFiles == null ? Collections.emptyList() : evidenceFiles;
        }
        public void setEvidenceFiles(List<String> evidenceFiles) { this.evidenceFiles = evidenceFiles; }
        public Map<String, Object> getMetadata() {
            return metadata == null ? Collections.emptyMap() : metadata;
        }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    private List<Suggestion> suggestions = new ArrayList<>();
    /** Diagnostic notes the plugin emitted while analysing — e.g. "skipped node_modules". */
    private List<String> notes = new ArrayList<>();

    public SuggestionResult() {}

    public List<Suggestion> getSuggestions() {
        return suggestions == null ? Collections.emptyList() : suggestions;
    }

    public void setSuggestions(List<Suggestion> suggestions) {
        this.suggestions = suggestions == null ? new ArrayList<>() : suggestions;
    }

    public List<String> getNotes() {
        return notes == null ? Collections.emptyList() : notes;
    }

    public void setNotes(List<String> notes) {
        this.notes = notes;
    }
}
