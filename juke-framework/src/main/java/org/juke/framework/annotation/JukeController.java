package org.juke.framework.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Spring {@code @RestController} for request/response capture during
 * REPLAY mode (Plan §5.4).
 *
 * <p>The application logic of the marked controller still executes normally —
 * upstream calls into {@code @Juke}-wrapped collaborators replay their recorded
 * data, and the controller's response is then diffed against the
 * {@code controller-capture/{ClassName}.{method}.{step}.json} sidecar inside
 * the recording ZIP. Mismatches mark the use case {@code FAILED} with
 * {@code failure_type = CONTROLLER_MISMATCH} but do not abort sibling use
 * cases.
 *
 * <p>During RECORD mode, the same advice persists the live request/response
 * back into the ZIP as the sidecar, establishing the baseline for future
 * replays.
 *
 * <pre>
 * &#64;JukeController
 * &#64;RestController
 * public class CurrencyController {
 *     &#64;GetMapping("/currencies")
 *     public List&lt;Currency&gt; list() { ... }
 * }
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JukeController {
}
