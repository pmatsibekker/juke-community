package org.juke.framework.coverage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative record of which concrete implementation classes Juke has
 * displaced with a record/replay proxy.
 *
 * <p>When the framework installs a {@code @Juke} proxy it physically swaps a
 * real bean out — so it already <em>knows</em> the implementation it replaced.
 * This registry simply captures that knowledge as a byproduct of the proxying
 * the framework does anyway. Nothing is discovered or guessed.
 *
 * <p>The sole consumer is functional code-coverage tooling (the
 * {@code juke-coverage} module): in replay mode a {@code @Juke}-mocked
 * implementation is never executed, so counting it would unfairly depress the
 * coverage figure for the application under test. The coverage report excludes
 * exactly the classes recorded here — and the developer never has to name an
 * implementation class, preserving the {@code @Juke} abstraction.
 *
 * <p>Populated at bean post-processing time (startup) by
 * {@code JukeBeanPostProcessor} (field-level {@code @Juke}) and
 * {@code JukeTypeBeanPostProcessor} (class-level {@code @Juke}); read later,
 * once, when a coverage report is requested. Thread-safe and process-wide.
 */
public final class JukeMockRegistry {

    /** Maps mocked interface FQN → displaced implementation class FQN. */
    private static final Map<String, String> IMPL_BY_INTERFACE = new ConcurrentHashMap<>();

    private JukeMockRegistry() {
    }

    /**
     * Records a field-level {@code @Juke} seam: the declared interface type and
     * the concrete class of the bean that was displaced by the proxy.
     *
     * @param mockedInterface the {@code @Juke} field's declared interface type
     * @param displacedImpl   the runtime class of the real bean that was replaced
     */
    public static void recordFieldMock(Class<?> mockedInterface, Class<?> displacedImpl) {
        if (mockedInterface == null || displacedImpl == null) {
            return;
        }
        IMPL_BY_INTERFACE.put(mockedInterface.getName(), unwrap(displacedImpl).getName());
    }

    /**
     * Records a class-level {@code @Juke} seam, where the concrete bean class is
     * itself the mocked unit (CGLIB-proxied in place).
     *
     * @param mockedConcreteClass the {@code @Juke}-annotated concrete class
     */
    public static void recordTypeMock(Class<?> mockedConcreteClass) {
        if (mockedConcreteClass == null) {
            return;
        }
        Class<?> concrete = unwrap(mockedConcreteClass);
        IMPL_BY_INTERFACE.put(concrete.getName(), concrete.getName());
    }

    /**
     * Returns the fully-qualified names of every implementation class Juke has
     * displaced — the set a coverage report should exclude.
     *
     * @return an unmodifiable snapshot; empty if nothing has been mocked
     */
    public static Set<String> getMockedImplementationClassNames() {
        return Set.copyOf(IMPL_BY_INTERFACE.values());
    }

    /**
     * Returns a snapshot of {@code mocked interface FQN → implementation FQN}
     * pairs, for transparent disclosure in the coverage report (e.g. "excluded
     * 1 class behind the @Juke seam IGreetingsService → GreetingServiceImpl").
     *
     * @return an unmodifiable, insertion-ordered snapshot
     */
    public static Map<String, String> getMockedImplementationsByInterface() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(IMPL_BY_INTERFACE));
    }

    /** Clears the registry — intended for test isolation only. */
    public static void clear() {
        IMPL_BY_INTERFACE.clear();
    }

    /**
     * Unwraps a Spring/CGLIB-generated proxy class (name contains {@code $$})
     * to the real superclass, so the recorded name matches the compiled
     * {@code .class} file the coverage analyzer will see.
     */
    private static Class<?> unwrap(Class<?> clazz) {
        Class<?> c = clazz;
        while (c != null && c.getName().contains("$$")) {
            Class<?> sup = c.getSuperclass();
            if (sup == null || sup == Object.class) {
                break;
            }
            c = sup;
        }
        return c;
    }
}
