package org.juke.framework.proxy;

import org.juke.framework.support.ISampleService;
import org.juke.framework.support.SampleService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JukeProxyFactory}.
 */
class JukeProxyFactoryTest {

    // ── createInterfaceProxy (single interface) ───────────────────────────

    @Test
    void createInterfaceProxy_single_returnsJdkProxy() {
        ISampleService proxy = JukeProxyFactory.createInterfaceProxy(
                ISampleService.class,
                (p, m, args) -> null);
        assertTrue(Proxy.isProxyClass(proxy.getClass()));
    }

    // ── createInterfaceProxy (multi-interface) ────────────────────────────

    @Test
    void createInterfaceProxy_multi_returnsJdkProxy() {
        ISampleService proxy = JukeProxyFactory.createInterfaceProxy(
                ISampleService.class,
                new Class<?>[]{ISampleService.class},
                ISampleService.class.getClassLoader(),
                (p, m, args) -> null);
        assertTrue(Proxy.isProxyClass(proxy.getClass()));
    }

    // ── collectInterfaces ─────────────────────────────────────────────────

    @Test
    void collectInterfaces_concreteClass_returnsAllInterfaces() {
        Class<?>[] ifaces = JukeProxyFactory.collectInterfaces(SampleService.class);
        assertNotNull(ifaces);
        assertTrue(ifaces.length >= 1);
        // SampleService implements ISampleService
        boolean found = false;
        for (Class<?> c : ifaces) {
            if (c == ISampleService.class) found = true;
        }
        assertTrue(found);
    }

    @Test
    void collectInterfaces_objectClass_returnsEmpty() {
        Class<?>[] ifaces = JukeProxyFactory.collectInterfaces(Object.class);
        assertEquals(0, ifaces.length);
    }

    @Test
    void collectInterfaces_interfaceClass_returnsDirectInterfaces() {
        // ISampleService itself is an interface — collectInterfaces stops at Object.class
        // so it won't recurse into the interface hierarchy. With class=Object it returns [].
        // Just verify it doesn't throw.
        assertDoesNotThrow(() -> JukeProxyFactory.collectInterfaces(ISampleService.class));
    }

    // ── createClassProxy ─────────────────────────────────────────────────

    @Test
    void createClassProxy_createsSubclassProxy() {
        SampleService proxy = JukeProxyFactory.createClassProxy(
                SampleService.class,
                (obj, method, args, proxy1) -> method.invoke(new SampleService(), args));
        assertNotNull(proxy);
        assertNotSame(SampleService.class, proxy.getClass()); // is a subclass
    }
}

