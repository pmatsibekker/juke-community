package org.juke.framework.proxy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Decides which methods should be intercepted for record/replay.
 * Excludes Object methods, bridge/synthetic methods, and non-public methods.
 */
public final class JukeMethodFilter {

    private static final Set<String> OBJECT_SIGNATURES = new HashSet<>();

    static {
        for (Method m : Object.class.getDeclaredMethods()) {
            OBJECT_SIGNATURES.add(signature(m));
        }
    }

    private JukeMethodFilter() { }

    public static boolean shouldIntercept(Method method) {
        if (method.getDeclaringClass() == Object.class) {
            return false;
        }
        if (OBJECT_SIGNATURES.contains(signature(method))) {
            return false;
        }
        if (method.isBridge() || method.isSynthetic()) {
            return false;
        }
        if (!Modifier.isPublic(method.getModifiers())) {
            return false;
        }
        return true;
    }

    private static String signature(Method m) {
        return m.getName() + ":" + Arrays.toString(m.getParameterTypes());
    }
}

