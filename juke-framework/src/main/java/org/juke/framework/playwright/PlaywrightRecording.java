package org.juke.framework.playwright;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a complete Playwright recording — an ordered list of request/response entries.
 * This is the top-level structure deserialized from a Playwright recording JSON file.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlaywrightRecording {

    private String name;
    private List<PlaywrightEntry> entries = new ArrayList<>();

    public PlaywrightRecording() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<PlaywrightEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<PlaywrightEntry> entries) {
        this.entries = entries;
    }
}

