package org.juke.framework.playwright;

import java.util.ArrayList;
import java.util.List;

/**
 * Full comparison report containing results for every endpoint call
 * and a summary of passed, failed, and ignored counts.
 */
public class ComparisonReport {

    private List<EndpointResult> results = new ArrayList<>();
    private Summary summary = new Summary();

    public ComparisonReport() {
    }

    public List<EndpointResult> getResults() {
        return results;
    }

    public void setResults(List<EndpointResult> results) {
        this.results = results;
    }

    public Summary getSummary() {
        return summary;
    }

    public void setSummary(Summary summary) {
        this.summary = summary;
    }

    public void addResult(EndpointResult result) {
        this.results.add(result);
        if ("PASS".equals(result.getStatus())) {
            summary.setPassed(summary.getPassed() + 1);
        } else {
            summary.setFailed(summary.getFailed() + 1);
        }
        summary.setIgnored(summary.getIgnored() + result.getIgnored().size());
    }

    /**
     * Summary counts across all endpoint results.
     */
    public static class Summary {
        private int passed;
        private int failed;
        private int ignored;

        public int getPassed() {
            return passed;
        }

        public void setPassed(int passed) {
            this.passed = passed;
        }

        public int getFailed() {
            return failed;
        }

        public void setFailed(int failed) {
            this.failed = failed;
        }

        public int getIgnored() {
            return ignored;
        }

        public void setIgnored(int ignored) {
            this.ignored = ignored;
        }
    }
}

