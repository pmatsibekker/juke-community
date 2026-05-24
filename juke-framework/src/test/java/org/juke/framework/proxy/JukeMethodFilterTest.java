package org.juke.framework.proxy;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JukeMethodFilter} — covers all shouldIntercept branches.
 */
class JukeMethodFilterTest {

    interface SampleIface {
        String doWork();
        String doWorkWithArg(String s);
    }

    static class SampleImpl implements SampleIface {
        @Override public String doWork() { return "work"; }
        @Override public String doWorkWithArg(String s) { return s; }
        // package-private — should not be intercepted
        String packagePrivate() { return "pkg"; }
    }

    @Test
    void shouldIntercept_publicInterfaceMethod_returnsTrue() throws Exception {
        Method m = SampleIface.class.getMethod("doWork");
        assertTrue(JukeMethodFilter.shouldIntercept(m));
    }

    @Test
    void shouldIntercept_objectMethod_returnsFalse() throws Exception {
        Method m = Object.class.getMethod("toString");
        assertFalse(JukeMethodFilter.shouldIntercept(m));
    }

    @Test
    void shouldIntercept_hashCode_returnsFalse() throws Exception {
        Method m = Object.class.getMethod("hashCode");
        assertFalse(JukeMethodFilter.shouldIntercept(m));
    }

    @Test
    void shouldIntercept_equals_returnsFalse() throws Exception {
        Method m = Object.class.getMethod("equals", Object.class);
        assertFalse(JukeMethodFilter.shouldIntercept(m));
    }

    @Test
    void shouldIntercept_publicMethodOnConcreteClass_returnsTrue() throws Exception {
        Method m = SampleImpl.class.getMethod("doWork");
        assertTrue(JukeMethodFilter.shouldIntercept(m));
    }

    @Test
    void shouldIntercept_packagePrivateMethod_returnsFalse() throws Exception {
        Method m = SampleImpl.class.getDeclaredMethod("packagePrivate");
        assertFalse(JukeMethodFilter.shouldIntercept(m));
    }
}

