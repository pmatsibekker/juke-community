package org.juke.framework.tuner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JukeCommandProcessorChain} – covers getInstance, setInstance, initialize.
 */
class JukeCommandProcessorChainTest {

    @BeforeEach
    void setUp() {
        TunerTask.setParticipants(new java.util.HashMap<>());
        TunerTaskRegistry.tuners.clear();
    }

    @AfterEach
    void tearDown() {
        TunerTask.setParticipants(new java.util.HashMap<>());
        TunerTaskRegistry.tuners.clear();
    }

    @Test
    void getInstance_isNotNullAfterStaticInit() {
        assertNotNull(JukeCommandProcessorChain.getInstance());
    }

    @Test
    void setInstance_replacesInstance() {
        JukeCommandProcessorChain original = JukeCommandProcessorChain.getInstance();
        JukeCommandProcessorChain newChain = new JukeCommandProcessorChain();
        JukeCommandProcessorChain.setInstance(newChain);
        assertSame(newChain, JukeCommandProcessorChain.getInstance());
        // restore
        JukeCommandProcessorChain.setInstance(original);
    }

    @Test
    void initialize_createsNewInstance() {
        JukeCommandProcessorChain.initialize();
        assertNotNull(JukeCommandProcessorChain.getInstance());
    }

    @Test
    void execute_withNoTunersForSignature_doesNotThrow() throws Exception {
        ProcessObject po = new ProcessObject();
        po.setSignature("no.match.signature");
        po.setJson("{}");
        // Should complete without exception (no tuner registered for this signature)
        JukeCommandProcessorChain.execute(po);
    }
}

