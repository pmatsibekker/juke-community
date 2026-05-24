package org.juke.framework.support;

/**
 * Test interface simulating a simplified RestTemplate-like client
 * where the SAME method name returns different types based on a Class<?> argument.
 *
 * This models the real-world problem:
 *   RestTemplate.getForEntity(URI url, Class<T> responseType) -> ResponseEntity<T>
 *
 * Juke wraps interfaces, so we model the scenario as an interface
 * with methods that return different types or use Class<?> arguments.
 */
public interface IRestClient {

    /**
     * Simulates a REST GET that always returns a String body.
     */
    String getAsString(String url);

    /**
     * Simulates a REST GET that always returns a SimpleResult body.
     */
    SimpleResult getAsResult(String url);

    /**
     * THE PROBLEM METHOD: same method signature called with different responseType
     * arguments, where the return type depends on the runtime Class<?> value.
     *
     * This mirrors RestTemplate.getForEntity(URI, Class<T>).
     * The return is Object because the real return type is determined by responseType.
     */
    Object getForEntity(String url, Class<?> responseType);

    /**
     * Overloaded version — same name, different parameter count.
     * Tests that Juke's overload detection still works alongside the type problem.
     */
    Object getForEntity(String url, Class<?> responseType, Object... uriVariables);
}

