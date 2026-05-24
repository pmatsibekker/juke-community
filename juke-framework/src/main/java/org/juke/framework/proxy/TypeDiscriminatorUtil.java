package org.juke.framework.proxy;

import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for detecting Class&lt;?&gt; arguments in method invocations and using them
 * as type discriminators for recording and replay.
 * <p>
 * This solves the RestTemplate.getForEntity(URI, Class&lt;T&gt;) problem where the
 * same method is called with different responseType arguments and Juke needs to
 * know which concrete type to deserialize into during replay.
 * </p>
 */
public class TypeDiscriminatorUtil {

    private static final Logger LOG = LoggerFactory.getLogger(TypeDiscriminatorUtil.class);

    private TypeDiscriminatorUtil() {
    }

    /**
     * Scans the method arguments for a Class&lt;?&gt; parameter and returns its canonical name.
     * This is used during RECORD to tag the ZIP entry, and during REPLAY to select
     * the correct deserialization type.
     *
     * @param method the method being invoked
     * @param args   the actual arguments passed to the invocation
     * @return the canonical class name of the first Class&lt;?&gt; argument, or null if none found
     */
    public static String extractTypeDiscriminator(Method method, Object[] args) {
        if (method == null || args == null) {
            return null;
        }
        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length && i < args.length; i++) {
            if (Class.class.isAssignableFrom(paramTypes[i]) && args[i] instanceof Class) {
                String name = ((Class<?>) args[i]).getCanonicalName();
                LOG.debug("Found type discriminator: {} at parameter index {}", name, i);
                return name;
            }
        }
        return null;
    }

    /**
     * Extracts the Class&lt;?&gt; value from arguments (the actual Class object, not its name).
     *
     * @param method the method being invoked
     * @param args   the actual arguments
     * @return the Class&lt;?&gt; argument value, or null if none found
     */
    public static Class<?> extractTypeDiscriminatorClass(Method method, Object[] args) {
        if (method == null || args == null) {
            return null;
        }
        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length && i < args.length; i++) {
            if (Class.class.isAssignableFrom(paramTypes[i]) && args[i] instanceof Class) {
                return (Class<?>) args[i];
            }
        }
        return null;
    }

    /**
     * Builds a ZIP entry identifier that includes a type discriminator.
     * <p>
     * Format with discriminator:  {@code com.example.IService.$method@com.example.Result}
     * Format without discriminator: {@code com.example.IService.$method} (backward compatible)
     * </p>
     * The '@' separator is chosen because it cannot appear in Java class or method names.
     *
     * @param baseIdentifier the standard identifier (interface.$method)
     * @param discriminator  the type discriminator class name, or null
     * @return the identifier with optional discriminator appended
     */
    public static String buildRecordIdentifier(String baseIdentifier, String discriminator) {
        if (discriminator == null || discriminator.isEmpty()) {
            return baseIdentifier;
        }
        return baseIdentifier + "@" + discriminator;
    }

    /**
     * Separator used between the method identifier and the type discriminator in ZIP entry names.
     */
    public static final String TYPE_SEPARATOR = "@";

    /**
     * Checks whether a ZIP entry name contains a type discriminator.
     *
     * @param entryName the ZIP entry name
     * @return true if the entry contains a type discriminator
     */
    public static boolean hasTypeDiscriminator(String entryName) {
        return entryName != null && entryName.contains(TYPE_SEPARATOR);
    }

    /**
     * Extracts the type discriminator class name from a ZIP entry name.
     *
     * @param entryName the ZIP entry name (e.g., "com.example.IService.$method@com.example.Result.1.json")
     * @return the discriminator class name, or null if none
     */
    public static String getDiscriminatorFromEntryName(String entryName) {
        if (entryName == null || !entryName.contains(TYPE_SEPARATOR)) {
            return null;
        }
        // Entry format: base@discriminator.N.json
        int atIdx = entryName.indexOf(TYPE_SEPARATOR);
        String afterAt = entryName.substring(atIdx + 1);
        // Strip the .N.json suffix by finding the last segment matching .\d+.json or .\d+
        int dotBeforeSeq = afterAt.lastIndexOf('.');
        if (dotBeforeSeq > 0) {
            // Could be ".json" or ".N" — walk back
            String candidate = afterAt.substring(0, dotBeforeSeq);
            int dotBeforeNum = candidate.lastIndexOf('.');
            if (dotBeforeNum > 0) {
                String numPart = candidate.substring(dotBeforeNum + 1);
                if (numPart.matches("\\d+")) {
                    return candidate.substring(0, dotBeforeNum);
                }
            }
            // The part before the last dot might be the discriminator itself
            return candidate;
        }
        return afterAt;
    }
}

