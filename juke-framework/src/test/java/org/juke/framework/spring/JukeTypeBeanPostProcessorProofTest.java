package org.juke.framework.spring;

import org.juke.framework.annotation.Juke;
import org.juke.framework.proxy.JukeState;
import org.juke.framework.runtime.JukeRuntimeHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proof-of-vulnerability test verifying that class-level @Juke beans
 * remain unwrapped (not proxied) at startup if the initial mode is IGNORE.
 */
public class JukeTypeBeanPostProcessorProofTest {

    @Juke
    public static class AnnotatedService {
        public String greet() { return "live"; }
    }

    private JukeTypeBeanPostProcessor processor;
    private String savedGlobal;

    @BeforeEach
    void setUp() {
        processor = new JukeTypeBeanPostProcessor();
        savedGlobal = JukeState.getGlobaljuke();
        JukeRuntimeHolder.reset();
    }

    @AfterEach
    void tearDown() {
        JukeState.setGlobaljuke(savedGlobal);
        JukeRuntimeHolder.reset();
    }

    @Test
    void proof_classLevelJuke_unwrapped_when_startup_mode_is_ignore() {
        // 1. Arrange global state to IGNORE (simulating default startup mode)
        JukeState.setGlobaljuke(JukeState.IGNORE);

        AnnotatedService service = new AnnotatedService();

        // 2. Act - process the bean
        Object processed = processor.postProcessAfterInitialization(service, "annotatedService");

        // 3. Assert - Expected to fail (assertNotSame fails) on the original codebase;
        // Should pass (a CGLIB proxy is returned) after the fix.
        assertNotSame(service, processed,
                "Expected JukeTypeBeanPostProcessor to wrap @Juke annotated class in a proxy even in IGNORE mode");
        assertTrue(processed.getClass().getName().contains("$$"),
                "Expected the processed bean to be a CGLIB proxy class containing '$$'");
    }
}
