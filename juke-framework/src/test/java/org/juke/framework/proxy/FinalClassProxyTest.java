package org.juke.framework.proxy;

import org.junit.jupiter.api.Test;
import org.springframework.cglib.proxy.MethodInterceptor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the CGLIB {@code final}-class limitation that the consolidation plan
 * relies on: {@link JukeProxyFactory#createClassProxy} (the mechanism behind
 * class-level {@code @Juke}) cannot subclass a {@code final} class, and fails
 * with a message that names the cause.
 */
class FinalClassProxyTest {

    static final class FinalUpstream {
        public String call() { return "real"; }
    }

    @Test
    void createClassProxy_onFinalClass_failsFastWithClearMessage() {
        MethodInterceptor passthrough = (obj, m, args, mp) -> mp.invokeSuper(obj, args);

        Throwable t = assertThrows(Throwable.class,
                () -> JukeProxyFactory.createClassProxy(FinalUpstream.class, passthrough));

        String msg = String.valueOf(t.getMessage()).toLowerCase();
        assertTrue(msg.contains("final") || msg.contains("subclass"),
                "expected a clear 'cannot subclass final class' message, got "
                        + t.getClass().getName() + ": " + t.getMessage());
    }
}
