package org.juke.framework.metadata;

import org.juke.framework.support.ISampleService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JukeParser}.
 */
class JukeParserTest {

    // ── getMethodName(Class, Method) ──────────────────────────────────────

    @Test
    void getMethodName_nonOverloaded_returnsSimpleName() throws Exception {
        // getMyDataMap(BigDecimal[], String) is declared once
        Method m = ISampleService.class.getMethod("getMyDataMap", BigDecimal[].class, String.class);
        String name = JukeParser.getMethodName(ISampleService.class, m);
        assertEquals("getMyDataMap", name);
    }

    @Test
    void getMethodName_toString_returnsClassToString() throws Exception {
        Method m = Object.class.getMethod("toString");
        String name = JukeParser.getMethodName(ISampleService.class, m);
        assertNotNull(name);
        assertTrue(name.contains("interface"));
    }

    @Test
    void getMethodName_overloaded_appendsSignatureHash() throws Exception {
        // getMyDataMapAsList is overloaded (two variants) in ISampleService
        Method m = ISampleService.class.getMethod("getMyDataMapAsList", BigDecimal[].class, String.class);
        String name = JukeParser.getMethodName(ISampleService.class, m);
        // overloaded → method name + numeric hash appended
        assertTrue(name.startsWith("getMyDataMapAsList"));
        assertNotEquals("getMyDataMapAsList", name);
    }

    // ── isOverloaded ──────────────────────────────────────────────────────

    @Test
    void isOverloaded_uniqueMethod_returnsFalse() {
        assertFalse(JukeParser.isOverloaded(ISampleService.class, "getMyDataMap"));
    }

    @Test
    void isOverloaded_overloadedMethod_returnsTrue() {
        assertTrue(JukeParser.isOverloaded(ISampleService.class, "getMyDataMapAsList"));
    }

    // ── buildParameterSignature ───────────────────────────────────────────

    @Test
    void buildParameterSignature_withParams_returnsConsistentInt() throws Exception {
        Method m = ISampleService.class.getMethod("getMyDataMap", BigDecimal[].class, String.class);
        int sig = JukeParser.buildParameterSignature(m);
        assertEquals(sig, JukeParser.buildParameterSignature(m));
    }

    @Test
    void buildParameterSignature_differentOverloads_differ() throws Exception {
        Method m1 = ISampleService.class.getMethod("getMyDataMapAsList", BigDecimal[].class, String.class);
        Method m2 = ISampleService.class.getMethod("getMyDataMapAsList", List.class, String.class);
        assertNotEquals(JukeParser.buildParameterSignature(m1), JukeParser.buildParameterSignature(m2));
    }

    // ── isAssignableFromArguments ─────────────────────────────────────────

    @Test
    void isAssignableFromArguments_nullArgs_noParams_returnsTrue() throws Exception {
        // fromSimpleDoubleArray has one param
        Method m = ISampleService.class.getMethod("fromSimpleDoubleArray", double[].class);
        assertFalse(JukeParser.isAssignableFromArguments(m, null));
    }

    @Test
    void isAssignableFromArguments_argCountMismatch_returnsFalse() throws Exception {
        Method m = ISampleService.class.getMethod("getMyDataMap", BigDecimal[].class, String.class);
        assertFalse(JukeParser.isAssignableFromArguments(m, new Object[]{"only-one-arg"}));
    }

    @Test
    void isAssignableFromArguments_correctTypes_returnsTrue() throws Exception {
        Method m = ISampleService.class.getMethod("getMyDataMap", BigDecimal[].class, String.class);
        assertTrue(JukeParser.isAssignableFromArguments(m, new Object[]{new BigDecimal[0], "key"}));
    }

    @Test
    void isAssignableFromArguments_wrongType_returnsFalse() throws Exception {
        Method m = ISampleService.class.getMethod("getMyDataMap", BigDecimal[].class, String.class);
        assertFalse(JukeParser.isAssignableFromArguments(m, new Object[]{42, "key"}));
    }

    @Test
    void isAssignableFromArguments_primitiveDoubleArray_returnsTrue() throws Exception {
        Method m = ISampleService.class.getMethod("fromSimpleDoubleArray", double[].class);
        assertTrue(JukeParser.isAssignableFromArguments(m, new Object[]{new double[]{1.0}}));
    }

    @Test
    void isAssignableFromArguments_nullArg_returnsTrue() throws Exception {
        // null arg is assignable to any type
        Method m = ISampleService.class.getMethod("getMyDataMap", BigDecimal[].class, String.class);
        assertTrue(JukeParser.isAssignableFromArguments(m, new Object[]{null, null}));
    }

    // ── isInMethods ───────────────────────────────────────────────────────

    @Test
    void isInMethods_found_returnsList() {
        Method[] methods = ISampleService.class.getMethods();
        List<Method> found = JukeParser.isInMethods("getMyDataMap", methods);
        assertNotNull(found);
        assertFalse(found.isEmpty());
    }

    @Test
    void isInMethods_notFound_returnsEmpty() {
        Method[] methods = ISampleService.class.getMethods();
        List<Method> found = JukeParser.isInMethods("nonExistentXYZ", methods);
        assertNotNull(found);
        assertTrue(found.isEmpty());
    }

    // ── cast ──────────────────────────────────────────────────────────────

    @Test
    void cast_returnsExpectedType() {
        Object value = "hello";
        String cast = JukeParser.cast(value);
        assertEquals("hello", cast);
    }
}

