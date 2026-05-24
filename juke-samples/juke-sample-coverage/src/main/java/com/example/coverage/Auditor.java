package com.example.coverage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logs greeting events. {@link #audit} is called by the demo flow on every
 * generated greeting; {@link #adminAudit} and {@link #dumpHistory} are
 * deliberately not invoked, so the server line-coverage figure stays below
 * 100% — a realistic figure that makes the threshold gate meaningful.
 *
 * <p>Click the "Royal" branch in the UI journey to bring the dashboard
 * coverage closer to 100% without ever calling these admin methods.
 */
@Component
public class Auditor {

    private static final Logger LOG = LoggerFactory.getLogger(Auditor.class);

    public void audit(String style, String name) {
        LOG.info("audit: style={} name={}", style, name);
    }

    /** Admin-only — intentionally unused by the demo flow. */
    public String adminAudit() {
        long now = System.currentTimeMillis();
        return "admin audit at " + now;
    }

    /** Admin-only — intentionally unused by the demo flow. */
    public String dumpHistory() {
        StringBuilder dump = new StringBuilder("history:\n");
        for (int i = 0; i < 3; i++) {
            dump.append("  entry ").append(i).append('\n');
        }
        return dump.toString();
    }
}
