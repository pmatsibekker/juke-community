package org.juke.framework.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a DTO field as a candidate for the {@code field_ignore_rule} table.
 *
 * <p>When a recording ZIP is registered, {@code IgnoreRuleSeeder} scans every
 * class referenced by the recording for fields carrying this annotation and
 * persists a {@code source = ANNOTATION} ignore rule for each one. The rule
 * applies to <em>both</em> the input-diff engine (Phase 3 §5.2) and the
 * controller mismatch comparison (§5.4).
 *
 * <h3>Strategies</h3>
 * <ul>
 *   <li><b>{@link IgnoreStrategy#ALWAYS}</b> — skip this field in every comparison
 *       regardless of value.</li>
 *   <li><b>{@link IgnoreStrategy#NOT_NULL}</b> — skip only when both sides are
 *       non-null; a null vs non-null difference is still a failure.</li>
 * </ul>
 *
 * <pre>
 * public class OrderRequest {
 *     &#64;JukeIgnorable                              // generated id — always skip
 *     private UUID orderId;
 *
 *     &#64;JukeIgnorable(strategy = IgnoreStrategy.NOT_NULL) // timestamp — value diff skipped, nullability checked
 *     private Instant submittedAt;
 *
 *     private String productCode;                 // compared normally
 * }
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface JukeIgnorable {

    /** How the diff engine should treat this field. Defaults to {@link IgnoreStrategy#ALWAYS}. */
    IgnoreStrategy strategy() default IgnoreStrategy.ALWAYS;

    /**
     * Per-field comparison strategy.
     * <p>Mirrors the {@code ignore_strategy} column on {@code field_ignore_rule}
     * (Plan §3.1). Kept as a nested type so the annotation contract is
     * self-contained.
     */
    enum IgnoreStrategy {
        /** Field is always skipped during comparison. */
        ALWAYS,
        /** Field is skipped only when both sides are non-null. */
        NOT_NULL
    }
}
