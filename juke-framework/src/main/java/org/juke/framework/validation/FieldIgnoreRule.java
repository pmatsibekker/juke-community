package org.juke.framework.validation;

import org.juke.framework.annotation.JukeIgnorable.IgnoreStrategy;

/**
 * Framework-side, persistence-agnostic projection of a row from
 * {@code field_ignore_rule} (Plan §3.1).
 *
 * <p>Decoupled from the JPA entity in {@code juke-scenario-service} so that
 * the framework module never compile-depends on the persistence module.
 * The scenario service is expected to map its entity to this record when
 * supplying rules to the {@link InputDiffEngine} via the
 * {@code IgnoreRuleProvider} sink.
 *
 * @param jsonPath JSONPath expression the rule applies to (e.g. {@code $.orderId})
 * @param strategy {@link IgnoreStrategy#ALWAYS} or {@link IgnoreStrategy#NOT_NULL}
 */
public record FieldIgnoreRule(String jsonPath, IgnoreStrategy strategy) {

    public FieldIgnoreRule {
        if (jsonPath == null || jsonPath.isBlank()) {
            throw new IllegalArgumentException("jsonPath cannot be null or blank");
        }
        if (strategy == null) {
            throw new IllegalArgumentException("strategy cannot be null");
        }
    }
}
