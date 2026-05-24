package org.juke.framework.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Spring {@code @RestController} for request/response capture during
 * RECORD and diff during REPLAY (Plan §5.4).
 *
 * <p>The application logic of the marked controller still executes normally —
 * upstream calls into {@code @Juke}-wrapped collaborators replay their recorded
 * data, and the controller's inbound request <i>and</i> response are then
 * diffed against two sidecars inside the recording ZIP:
 * <ul>
 *   <li>{@code controller-capture/{Class}.{method}.{step}.request.json} —
 *       {@code method}, {@code uri}, {@code query} parameters, and the
 *       {@code @RequestBody} payload (if any). Headers are intentionally
 *       excluded.</li>
 *   <li>{@code controller-capture/{Class}.{method}.{step}.json} — the
 *       controller's response object (body + status).</li>
 * </ul>
 *
 * <p>Both sides honour {@link JukeIgnorable} on the request DTO and response
 * type's fields, so a non-deterministic value (a freshly generated id, a
 * timestamp, &c.) does not produce a spurious mismatch. The framework
 * reflects on these annotations directly — no scenario-service
 * {@code IgnoreRuleProvider} is required for {@code @JukeIgnorable} to take
 * effect.
 *
 * <p>Mismatches are logged as {@code CONTROLLER_MISMATCH [Class.method#step]:
 * REQ[...]} or {@code RESP[...]} and (when a scenario {@code ReplayContext} is
 * active) published to the scenario sink with {@code failure_type =
 * CONTROLLER_MISMATCH}, but they never abort the request — the controller's
 * response is always returned to the caller unchanged.
 *
 * <p>The advice fires equally in:
 * <ul>
 *   <li><b>global RECORD</b> (via {@code /service/record/start}) — writes both
 *       sidecars to the ZIP being recorded.</li>
 *   <li><b>global REPLAY</b> (via {@code /service/replay/start}) — diffs
 *       against both sidecars in the configured track.</li>
 *   <li><b>cookie-session REPLAY</b> (via {@code /service/session/start}) —
 *       diffs against the per-session DAO, so concurrent sessions do not
 *       interfere with each other.</li>
 * </ul>
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
