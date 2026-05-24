package org.juke.framework.tuner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ProcessObject} – covers all getters and setters.
 */
class ProcessObjectTest {

    @Test
    void signatureGetterAndSetter() {
        ProcessObject po = new ProcessObject();
        assertNull(po.getSignature());
        po.setSignature("my.Service.$method.1");
        assertEquals("my.Service.$method.1", po.getSignature());
    }

    @Test
    void objectGetterAndSetter() {
        ProcessObject po = new ProcessObject();
        assertNull(po.getObject());
        Object obj = new Object();
        po.setObject(obj);
        assertSame(obj, po.getObject());
    }

    @Test
    void jsonGetterAndSetter() {
        ProcessObject po = new ProcessObject();
        assertNull(po.getJson());
        po.setJson("{\"key\":\"value\"}");
        assertEquals("{\"key\":\"value\"}", po.getJson());
    }

    @Test
    void directFieldAccess() {
        ProcessObject po = new ProcessObject();
        po.signature = "direct";
        po.json = "{}";
        po.object = 42;
        assertEquals("direct", po.getSignature());
        assertEquals("{}", po.getJson());
        assertEquals(42, po.getObject());
    }
}

