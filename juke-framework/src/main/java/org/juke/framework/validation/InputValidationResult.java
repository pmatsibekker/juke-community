package org.juke.framework.validation;

import java.util.List;

/**
 * Outcome of {@link InputDiffEngine#diff} for a single replayed call.
 *
 * <p>An empty {@link #diffs()} list means the live arguments matched the
 * recording (modulo any ignore rules). A non-empty list signals an
 * {@code INPUT_MISMATCH} that the scenario service should record against
 * the per-use-case test result row.
 *
 * @param className  fully-qualified class of the call site
 * @param methodName method being replayed
 * @param step       recording step number (sequence within the use case)
 * @param diffs      one entry per mismatching leaf path; empty = match
 */
public record InputValidationResult(
        String className,
        String methodName,
        int step,
        List<FieldDiff> diffs) {

    /** @return {@code true} when at least one field diff was found. */
    public boolean hasDiffs() {
        return diffs != null && !diffs.isEmpty();
    }

    /** Compact, single-line summary suitable for log messages and event payloads. */
    public String summary() {
        if (!hasDiffs()) return "no diffs";
        StringBuilder sb = new StringBuilder()
                .append(className).append('.').append(methodName)
                .append('[').append(step).append("]: ");
        for (int i = 0; i < diffs.size(); i++) {
            if (i > 0) sb.append("; ");
            FieldDiff d = diffs.get(i);
            sb.append(d.jsonPath())
              .append(" expected=").append(d.expected())
              .append(" actual=").append(d.actual());
        }
        return sb.toString();
    }
}
