package org.juke.framework.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Produces short, human-readable names for Juke ZIP entries and maintains
 * a mapping back to fully-qualified identifiers for deserialization.
 *
 * <h3>No collision (default):</h3>
 * <pre>
 * IDataService.fetchData@WeatherReport.1.json
 * </pre>
 *
 * <h3>With collision (e.g. com.example.MyService and com.other.MyService):</h3>
 * <pre>
 * example.MyService.getData@Result.1.json
 * * other.MyService.getData@Result.1.json
 * </pre>
 *
 * <h3>juke-mappings.json entry:</h3>
 * <pre>
 * {
 *   "IDataService.fetchData@WeatherReport": {
 *     "interfaceFqn": "org.juke.framework.example...IDataService",
 *     "method": "fetchData",
 *     "signature": "fetchData(String, Class) : Object",
 *     "responseType": "org.juke.framework.example...WeatherReport"
 *   }
 * }
 * </pre>
 */
public class JukeNameFormatter {

    private static final Logger LOG = LoggerFactory.getLogger(JukeNameFormatter.class);

    /**
     * Thread-safe accumulator of entry mappings produced during RECORD.
     * Written into juke-mappings.json at flush time so REPLAY can resolve short names.
     */
    private static final Map<String, EntryMapping> entryMappings =
            Collections.synchronizedMap(new LinkedHashMap<>());

    /**
     * Tracks which FQNs have been registered under each simple name.
     * Key = simple class name (e.g. "MyService"), Value = set of FQNs that map to it.
     * Used to detect collisions and trigger disambiguation.
     */
    private static final Map<String, Set<String>> simpleNameRegistry =
            Collections.synchronizedMap(new HashMap<>());

    private JukeNameFormatter() {}

    // -- Public API -------------------------------------------------------

    /**
     * Builds a short, human-readable base identifier for a ZIP entry.
     * If the interface or type discriminator simple name collides with another
     * registered FQN, the minimum distinguishing package prefix is used.
     *
     * @param interfaceClass    the proxy interface (e.g. IDataService)
     * @param methodName        the method name produced by JukeParser (may include hash)
     * @param typeDiscriminator the canonical name of the Class argument, or null
     * @return short identifier like "IDataService.fetchData@WeatherReport"
     */
    public static String buildShortIdentifier(Class<?> interfaceClass,
                                              String methodName,
                                              String typeDiscriminator) {
        String interfaceFqn = interfaceClass.getName();
        String shortInterface = disambiguatedName(interfaceFqn);
        String shortMethod = stripDollarPrefix(cleanMethodName(methodName));
        String base = shortInterface + "." + shortMethod;

        if (typeDiscriminator != null && !typeDiscriminator.isEmpty()) {
            base += "@" + disambiguatedName(typeDiscriminator);
        }
        return base;
    }

    /**
     * Builds a short identifier and registers a mapping entry so the full
     * context can be recovered during REPLAY or human inspection.
     *
     * @param interfaceClass    the proxy interface
     * @param method            the actual java.lang.reflect.Method being recorded
     * @param methodName        the JukeParser-produced method name
     * @param typeDiscriminator canonical name of the Class argument, or null
     * @return short identifier
     */
    public static String buildAndRegister(Class<?> interfaceClass,
                                          Method method,
                                          String methodName,
                                          String typeDiscriminator) {
        // Register FQNs for collision detection BEFORE building the identifier
        registerFqn(interfaceClass.getName());
        if (typeDiscriminator != null && !typeDiscriminator.isEmpty()) {
            registerFqn(typeDiscriminator);
        }

        String shortId = buildShortIdentifier(interfaceClass, methodName, typeDiscriminator);

        EntryMapping mapping = new EntryMapping();
        mapping.interfaceFqn = interfaceClass.getName();
        mapping.interfaceCanonical = interfaceClass.getCanonicalName();
        mapping.method = method.getName();
        mapping.signature = buildHumanSignature(method);
        mapping.responseType = typeDiscriminator;
        mapping.methodNameKey = methodName;

        entryMappings.put(shortId, mapping);
        LOG.debug("Registered entry mapping: {} -> {}", shortId, mapping);
        return shortId;
    }

    /**
     * Returns accumulated entry mappings (for serialization into juke-mappings.json).
     */
    public static Map<String, EntryMapping> getEntryMappings() {
        return new LinkedHashMap<>(entryMappings);
    }

    /**
     * Clears accumulated mappings and collision registry (for test isolation).
     */
    public static void clearMappings() {
        entryMappings.clear();
        simpleNameRegistry.clear();
    }

    // -- Collision Detection & Disambiguation -----------------------------

    /**
     * Registers a fully-qualified name so that future calls to
     * {@link #disambiguatedName(String)} can detect collisions.
     *
     * @param fqn a fully-qualified class name (e.g. "com.example.MyService")
     */
    public static void registerFqn(String fqn) {
        if (fqn == null || fqn.isEmpty()) return;
        String simple = simpleName(fqn);
        simpleNameRegistry.computeIfAbsent(simple, k -> Collections.synchronizedSet(new LinkedHashSet<>())).add(fqn);
    }

    /**
     * Returns the shortest unambiguous name for a fully-qualified name.
     * <ul>
     *   <li>If no collision: returns the simple name (e.g. "MyService")</li>
     *   <li>If collision: returns the minimum distinguishing prefix
     *       (e.g. "example.MyService" vs "other.MyService")</li>
     * </ul>
     *
     * @param fqn the fully-qualified name
     * @return the shortest unambiguous name
     */
    public static String disambiguatedName(String fqn) {
        if (fqn == null || fqn.isEmpty()) return "";
        String simple = simpleName(fqn);
        Set<String> registered = simpleNameRegistry.get(simple);

        // No collision: just use the simple name
        if (registered == null || registered.size() <= 1) {
            return simple;
        }

        // Collision detected -- find the minimum distinguishing prefix
        return minDistinguishingName(fqn, registered);
    }

    /**
     * Given a FQN and a set of colliding FQNs, returns the minimum number of
     * trailing package segments needed to make this name unique.
     * <p>
     * Example: "com.example.api.MyService" collides with "com.other.svc.MyService"
     * <br>Result: "api.MyService" vs "svc.MyService"
     * <p>
     * Example: "com.example.MyService" collides with "com.other.MyService"
     * <br>Result: "example.MyService" vs "other.MyService"
     */
    static String minDistinguishingName(String fqn, Set<String> allFqns) {
        // Split all FQNs into segments (handling both . and $ separators)
        String[] myParts = splitFqn(fqn);

        // Start with just the simple name (1 segment from the end)
        // and keep adding segments until unique among the set
        for (int depth = 1; depth <= myParts.length; depth++) {
            String candidate = joinTail(myParts, depth);
            boolean unique = true;
            for (String other : allFqns) {
                if (other.equals(fqn)) continue;
                String[] otherParts = splitFqn(other);
                String otherCandidate = joinTail(otherParts, depth);
                if (candidate.equals(otherCandidate)) {
                    unique = false;
                    break;
                }
            }
            if (unique) {
                return candidate;
            }
        }
        // Fallback: use the full FQN (should never happen unless all FQNs are identical)
        return fqn;
    }

    /**
     * Splits "com.example.Outer$Inner" into ["com", "example", "Outer", "Inner"].
     */
    public static String[] splitFqn(String fqn) {
        return fqn.split("[.$]");
    }

    /**
     * Joins the last N segments with ".".
     * e.g. joinTail(["com","example","api","MyService"], 2) = "api.MyService"
     */
    public static String joinTail(String[] parts, int count) {
        int start = Math.max(0, parts.length - count);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < parts.length; i++) {
            if (sb.length() > 0) sb.append(".");
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    // -- Mapping POJO -----------------------------------------------------

    /**
     * Serialized into juke-mappings.json so a human (or REPLAY) can understand
     * what each short entry name maps to.
     */
    public static class EntryMapping {
        private String interfaceFqn;
        private String interfaceCanonical;
        private String method;
        private String signature;
        private String responseType;
        private String methodNameKey;

        public EntryMapping() {}

        // Jackson needs getters/setters
        public String getInterfaceFqn() { return interfaceFqn; }
        public void setInterfaceFqn(String v) { this.interfaceFqn = v; }
        public String getInterfaceCanonical() { return interfaceCanonical; }
        public void setInterfaceCanonical(String v) { this.interfaceCanonical = v; }
        public String getMethod() { return method; }
        public void setMethod(String v) { this.method = v; }
        public String getSignature() { return signature; }
        public void setSignature(String v) { this.signature = v; }
        public String getResponseType() { return responseType; }
        public void setResponseType(String v) { this.responseType = v; }
        public String getMethodNameKey() { return methodNameKey; }
        public void setMethodNameKey(String v) { this.methodNameKey = v; }

        @Override
        public String toString() {
            return interfaceFqn + "." + method + "(" + signature + ")"
                    + (responseType != null ? " -> " + responseType : "");
        }
    }

    // -- Internal helpers -------------------------------------------------

    /**
     * Extracts the simple class name from a fully-qualified or internal name.
     * Handles both "com.example.Outer$Inner" and "com.example.Outer.Inner".
     */
    public static String simpleName(String fqn) {
        if (fqn == null) return "";
        int lastDot = fqn.lastIndexOf('.');
        int lastDollar = fqn.lastIndexOf('$');
        int cutPoint = Math.max(lastDot, lastDollar);
        return cutPoint >= 0 ? fqn.substring(cutPoint + 1) : fqn;
    }

    /**
     * Removes the leading "$" that JukeParser prepends to method names.
     */
    static String stripDollarPrefix(String name) {
        return (name != null && name.startsWith("$")) ? name.substring(1) : name;
    }

    /**
     * Removes the overload hash suffix that JukeParser appends
     * (e.g. "fetchData1679564109" -> "fetchData").
     * The hash is an integer appended directly to the method name with no separator.
     */
    public static String cleanMethodName(String name) {
        if (name == null) return "";
        // Walk backwards to find where digits start
        int i = name.length() - 1;
        while (i >= 0 && Character.isDigit(name.charAt(i))) {
            i--;
        }
        // Also consume a leading '-' for negative hashCode values (e.g. "getData-1641989625")
        if (i >= 0 && name.charAt(i) == '-') {
            i--;
        }
        if (i < name.length() - 1 && i >= 0) {
            String suffix = name.substring(i + 1);
            // Strip '-' for length check
            String digits = suffix.startsWith("-") ? suffix.substring(1) : suffix;
            if (digits.length() >= 5) {
                return name.substring(0, i + 1);
            }
        }
        return name;
    }

    /**
     * Builds a human-readable method signature like "fetchData(String, Class) : Object".
     */
    static String buildHumanSignature(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getName()).append("(");
        Type[] params = method.getGenericParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(simpleName(params[i].getTypeName()));
        }
        sb.append(")");
        String ret = method.getGenericReturnType().getTypeName();
        sb.append(" : ").append(simpleName(ret));
        return sb.toString();
    }
}
