package org.juke.framework.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a DTO field that should be ignored during recording/replay comparison.
 *
 * <p>The annotation is honoured in three places, none of which require any
 * external configuration — the framework reflects on the live argument /
 * request DTO / response type and produces the corresponding ignore rules
 * directly via {@code JukeIgnorableScanner}:
 * <ul>
 *   <li>the {@code @Juke} seam's per-call input comparison (so a recorded
 *       argument whose only difference is a {@code @JukeIgnorable} field still
 *       counts as a match);</li>
 *   <li>{@code @JukeController}'s inbound request diff (paths anchored at
 *       {@code $.body.…});</li>
 *   <li>{@code @JukeController}'s outbound response diff (paths anchored at
 *       {@code $.…}).</li>
 * </ul>
 * In addition, Enterprise's {@code IgnoreRuleSeeder} reads the same annotation
 * to populate the {@code field_ignore_rule} table for the scenario service —
 * the two paths produce equivalent rules so both flows behave identically.
 *
 * <p>The annotation applies on both DTO classes and record components: in
 * {@code record Foo(@JukeIgnorable String x)} the JLS propagates the
 * annotation to the underlying field, so the same reflection picks it up.
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
