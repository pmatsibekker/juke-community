package org.juke.framework.playwright;

import java.util.ArrayList;
import java.util.List;

/**
 * Comparison result for a single endpoint call.
 */
public class EndpointResult {

    private String endpoint;
    private int callIndex;
    private String status; // "PASS" or "FAIL"
    private List<JsonDiff> diffs = new ArrayList<>();
    private List<String> ignored = new ArrayList<>();

    public EndpointResult() {
    }

    public EndpointResult(String endpoint, int callIndex) {
        this.endpoint = endpoint;
        this.callIndex = callIndex;
        this.status = "PASS";
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public int getCallIndex() {
        return callIndex;
    }

    public void setCallIndex(int callIndex) {
        this.callIndex = callIndex;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<JsonDiff> getDiffs() {
        return diffs;
    }

    public void setDiffs(List<JsonDiff> diffs) {
        this.diffs = diffs;
    }

    public List<String> getIgnored() {
        return ignored;
    }

    public void setIgnored(List<String> ignored) {
        this.ignored = ignored;
    }

    public void addDiff(JsonDiff diff) {
        this.diffs.add(diff);
        this.status = "FAIL";
    }

    public void addIgnored(String path) {
        this.ignored.add(path);
    }
}

