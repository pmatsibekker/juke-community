package org.juke.framework.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that indicates a field, method, constructor parameter, or class
 * should be wrapped with JukeFactory for record/replay.
 *
 * <h3>Field / parameter usage (wraps an injected dependency):</h3>
 * <pre>
 * &#64;Autowired
 * &#64;Juke
 * private IGreetingsService service;
 * </pre>
 * The bare {@code @Juke} uses the default value {@code "juke"} (record globally,
 * replay per-session). Pass an explicit value only when you need a different
 * state — {@code @Juke("none")}, {@code @Juke("record")}, etc.
 * 
 * <h3>Type-level usage (wraps the entire component — works with concrete classes):</h3>
 * <pre>
 * &#64;Juke
 * &#64;Service
 * public class MyService implements InterfaceA, InterfaceB {
 *     public String a() { ... }   // recorded as MyService.a
 *     public String b() { ... }   // recorded as MyService.b
 * }
 * </pre>
 * When placed on a class, <b>all public methods</b> from the class and every
 * implemented interface are recorded/replayed under the <em>concrete class</em>
 * identity.  Methods from {@code java.lang.Object} are never intercepted.
 *
 * <h3>Concrete-typed fields (e.g. a Spring {@code RestTemplate}):</h3>
 * <pre>
 * &#64;Autowired
 * &#64;Juke(name = "shipping", excludeMethods = {"setRequestFactory"})
 * private RestTemplate restTemplate;     // wrapped in a CGLIB subclass
 * </pre>
 * An interface-typed field gets a JDK dynamic proxy (and per-session replay); a
 * concrete-typed field gets a CGLIB subclass that delegates to the real bean.
 * {@link #name()} and {@link #excludeMethods()} apply to the concrete case —
 * this is the single, standard way to intercept a concrete dependency such as a
 * {@code RestTemplate}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface Juke {
    /**
     * The JukeState to use. Supported values: "juke", "record", "replay", "ignore", "none", "disable".
     * Defaults to "juke".
     * @return the JukeState value
     */
    String value() default "juke";
    
    /**
     * Whether to automatically wrap this field/parameter. Defaults to true.
     * Set to false to disable automatic wrapping (useful for conditional wrapping).
     * @return true if automatic wrapping should be performed
     */
    boolean autoWrap() default true;

    /**
     * Logical name used as the recording-identity prefix for a <b>concrete-typed
     * field</b>. Disambiguates multiple beans of the same type (e.g. two
     * {@code RestTemplate}s). When empty, the field's declared type simple name
     * is used. Ignored for interface-typed fields and class-level usage.
     * @return the recording name override, or "" to use the type's simple name
     */
    String name() default "";

    /**
     * Method names to exclude from interception on a <b>concrete-typed field</b>
     * (e.g. builder/config methods like {@code setMessageConverters} that you do
     * not want recorded). {@code Object} methods are always excluded. Ignored for
     * interface-typed fields and class-level usage.
     * @return method names to skip
     */
    String[] excludeMethods() default {};
}