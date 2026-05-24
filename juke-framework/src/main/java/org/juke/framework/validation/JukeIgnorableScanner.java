package org.juke.framework.validation;

import org.juke.framework.annotation.JukeIgnorable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Derives {@link FieldIgnoreRule}s directly from {@link JukeIgnorable}
 * annotations on a live invocation's argument types.
 *
 * <p>The Enterprise scenario service seeds ignore rules from a database table;
 * Community has no such store, so the annotation would otherwise be inert at
 * replay time. This scanner reads the annotations off the argument objects
 * themselves, producing rules whose {@code jsonPath} matches the path scheme
 * {@link InputDiffEngine} walks when it diffs the serialized argument array:
 * the root is the argument list, so the first argument is {@code $[0]} and a
 * field {@code foo} on it is {@code $[0].foo}.
 *
 * <p>Nested non-JDK types are scanned recursively; a stack guard prevents
 * infinite recursion on self-referential models. Collections, maps, arrays
 * and JDK types are not descended into.
 */
public final class JukeIgnorableScanner {

    private JukeIgnorableScanner() {}

    /**
     * Scans the live argument array for {@link JukeIgnorable} fields.
     *
     * @param args the live invocation arguments (may be null)
     * @return ignore rules keyed by InputDiffEngine-style json paths; never null
     */
    public static List<FieldIgnoreRule> scanArgs(Object[] args) {
        List<FieldIgnoreRule> rules = new ArrayList<>();
        if (args == null) {
            return rules;
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i] != null) {
                scanType(args[i].getClass(), "$[" + i + "]", rules, new HashSet<>());
            }
        }
        return rules;
    }

    /**
     * Scans a single type for {@link JukeIgnorable} fields, anchoring the
     * generated rules at the supplied {@code rootPath}.
     *
     * <p>Used by the controller-advice request/response diff so that
     * {@code @JukeIgnorable} on a controller DTO's fields works without
     * requiring a scenario-service {@code IgnoreRuleProvider} to be installed.
     *
     * @param rootType the runtime type to walk (may be {@code null} → empty list)
     * @param rootPath the JSON path prefix the rules should be anchored at
     *                 (e.g. {@code "$"} for a response, {@code "$.body"} for a
     *                 captured request body). Must not be {@code null}.
     * @return ignore rules under {@code rootPath}; never null
     */
    public static List<FieldIgnoreRule> scan(Class<?> rootType, String rootPath) {
        if (rootPath == null) {
            throw new IllegalArgumentException("rootPath cannot be null");
        }
        List<FieldIgnoreRule> rules = new ArrayList<>();
        if (rootType != null) {
            scanType(rootType, rootPath, rules, new HashSet<>());
        }
        return rules;
    }

    private static void scanType(Class<?> type, String basePath,
                                 List<FieldIgnoreRule> out, Set<Class<?>> onStack) {
        if (!isScannable(type) || onStack.contains(type)) {
            return;
        }
        onStack.add(type);
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
                    continue;
                }
                String path = basePath + "." + f.getName();
                JukeIgnorable ann = f.getAnnotation(JukeIgnorable.class);
                if (ann != null) {
                    out.add(new FieldIgnoreRule(path, ann.strategy()));
                } else {
                    scanType(f.getType(), path, out, onStack);
                }
            }
        }
        onStack.remove(type);
    }

    /**
     * A type is worth descending into only when it is a user-defined POJO.
     * Primitives, enums, arrays, JDK types, collections and maps carry no
     * scannable nested {@code @JukeIgnorable} fields for our purposes.
     */
    private static boolean isScannable(Class<?> type) {
        if (type == null || type.isPrimitive() || type.isEnum() || type.isArray()) {
            return false;
        }
        if (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
            return false;
        }
        String name = type.getName();
        return !name.startsWith("java.") && !name.startsWith("javax.") && !name.startsWith("jakarta.");
    }
}
