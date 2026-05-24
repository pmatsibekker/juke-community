package com.example.coverage;

/**
 * Composes the textual content of a greeting. This is the Juke seam — the
 * interface {@code @Juke} intercepts at the DAO layer. In replay mode the
 * proxy short-circuits the call before it reaches {@link GreetingComposerImpl},
 * so the implementation never executes and would unfairly depress the coverage
 * figure. {@code juke-coverage} solves that automatically: every class
 * registered as a Juke-displaced implementation is excluded from the JaCoCo
 * analysis and listed in the {@code excludedSeams} field of the response.
 */
public interface IGreetingComposer {
    Greeting compose(String name, String style);
}
