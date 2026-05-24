package org.juke.framework.proxy;

import org.juke.framework.config.JukeSpringContextHolder;
import org.juke.framework.runtime.JukeRuntimeHolder;
import org.juke.framework.session.JukeSessionContext;
import org.juke.framework.session.SessionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.support.SimpleThreadScope;

import java.lang.reflect.Proxy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Verifies the claim that offloading a {@code @Juke} call to a worker thread
 * loses the session/replay context — the reason the exceptions sample detects
 * "queued" on the client, not via a server-side executor.
 *
 * <p>{@code JukeSessionContext} is a {@code @Scope("request")} bean (see
 * {@code JukeConfiguration}). Request scope is bound to the request thread via a
 * non-inheritable {@code ThreadLocal}, so a worker thread cannot see it. We model
 * that thread affinity with Spring's {@link SimpleThreadScope} (also a
 * non-inheritable per-thread scope) and show that the *same*
 * {@code JukeFactory.newInstance} call yields session routing on the request
 * thread but loses it on a worker thread.
 *
 * <p>Complements {@code JukeFactoryNewInstanceTest#newInstance_sessionContextLookupFails_fallsBackToNormalFlow},
 * which covers the other half: when the session-context lookup throws (what a
 * real {@code @RequestScope} bean does off-thread), JukeFactory falls back to the
 * global flow.
 */
class ExecutorSessionContextLossTest {

    public interface IPing { String ping(); }
    public static class PingImpl implements IPing { public String ping() { return "real"; } }

    private String savedGlobal;

    @BeforeEach
    void setUp() {
        savedGlobal = JukeState.getGlobaljuke();
        JukeRuntimeHolder.reset();
    }

    @AfterEach
    void tearDown() {
        JukeState.setGlobaljuke(savedGlobal);
        JukeRuntimeHolder.reset();
        new JukeSpringContextHolder().setApplicationContext(null);
    }

    @Test
    void sessionRouting_isLost_whenSeamCallIsOffloadedToWorkerThread() throws Exception {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.getBeanFactory().registerScope("request", new SimpleThreadScope());
        GenericBeanDefinition bd = new GenericBeanDefinition();
        bd.setBeanClass(JukeSessionContext.class);
        bd.setScope("request");
        ctx.registerBeanDefinition("jukeSessionContext", bd);
        ctx.getBeanFactory().registerSingleton("sessionRegistry", mock(SessionRegistry.class));
        ctx.refresh();
        new JukeSpringContextHolder().setApplicationContext(ctx);

        JukeState.setGlobaljuke(JukeState.NONE);                 // global mode = passthrough
        ctx.getBean(JukeSessionContext.class).setPlaybackActive(true); // session active on THIS thread

        // Request thread: the active session is visible → session-aware proxy.
        IPing onRequestThread = new JukeFactory<IPing>()
                .newInstance(new PingImpl(), IPing.class, JukeState.JUKE);
        assertTrue(Proxy.isProxyClass(onRequestThread.getClass()),
                "on the request thread the active session should yield a proxy");
        assertTrue(onRequestThread.toString().contains("JukeSessionReplayProxy"),
                "...routed specifically through the session-aware handler");

        // Worker thread: the request-scoped session is NOT visible (fresh, inactive),
        // so routing falls back to global mode (NONE) and returns the RAW bean.
        ExecutorService ex = Executors.newSingleThreadExecutor();
        try {
            PingImpl wrapped = new PingImpl();
            IPing onWorker = ex.submit(() -> new JukeFactory<IPing>()
                    .newInstance(wrapped, IPing.class, JukeState.JUKE)).get();
            assertSame(wrapped, onWorker,
                    "offloaded to a worker thread, the session context is invisible → "
                    + "routing falls back to global mode and returns the unwrapped bean");
        } finally {
            ex.shutdownNow();
        }
    }
}
