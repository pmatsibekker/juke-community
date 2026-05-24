package org.juke.framework.metadata;

import com.fasterxml.jackson.databind.JavaType;
import org.juke.framework.support.ISampleService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JukeConfigBuilder}.
 */
class JukeConfigBuilderTest {

    // ── convertSimpleTypeToObject ─────────────────────────────────────────

    @Test
    void convertSimpleTypeToObject_int() {
        assertEquals("Integer", JukeConfigBuilder.convertSimpleTypeToObject("int"));
    }

    @Test
    void convertSimpleTypeToObject_double() {
        assertEquals("Double", JukeConfigBuilder.convertSimpleTypeToObject("double"));
    }

    @Test
    void convertSimpleTypeToObject_boolean() {
        assertEquals("Boolean", JukeConfigBuilder.convertSimpleTypeToObject("boolean"));
    }

    @Test
    void convertSimpleTypeToObject_long() {
        assertEquals("Long", JukeConfigBuilder.convertSimpleTypeToObject("long"));
    }

    @Test
    void convertSimpleTypeToObject_char() {
        assertEquals("Character", JukeConfigBuilder.convertSimpleTypeToObject("char"));
    }

    @Test
    void convertSimpleTypeToObject_float() {
        assertEquals("Float", JukeConfigBuilder.convertSimpleTypeToObject("float"));
    }

    @Test
    void convertSimpleTypeToObject_short() {
        assertEquals("Short", JukeConfigBuilder.convertSimpleTypeToObject("short"));
    }

    @Test
    void convertSimpleTypeToObject_byte() {
        assertEquals("Byte", JukeConfigBuilder.convertSimpleTypeToObject("byte"));
    }

    @Test
    void convertSimpleTypeToObject_objectType_passThrough() {
        assertEquals("java.lang.String", JukeConfigBuilder.convertSimpleTypeToObject("java.lang.String"));
    }

    @Test
    void convertSimpleTypeToObject_intArray() {
        assertEquals("Integer[]", JukeConfigBuilder.convertSimpleTypeToObject("int[]"));
    }

    @Test
    void convertSimpleTypeToObject_doubleArray() {
        assertEquals("Double[]", JukeConfigBuilder.convertSimpleTypeToObject("double[]"));
    }

    // ── set / build ───────────────────────────────────────────────────────

    @Test
    void set_build_returnsJukeClass() {
        JukeClass jc = JukeConfigBuilder.set(ISampleService.class).build();
        assertNotNull(jc);
        assertEquals(ISampleService.class.getCanonicalName(), jc.getClassName());
    }

    @Test
    void build_populatesMethods() {
        JukeClass jc = JukeConfigBuilder.set(ISampleService.class).build();
        assertNotNull(jc.getMethods());
        assertFalse(jc.getMethods().isEmpty());
    }

    // ── toJSON / fromJSON ─────────────────────────────────────────────────

    @Test
    void toJSON_producesNonEmptyString() throws Exception {
        JukeClass jc = JukeConfigBuilder.set(ISampleService.class).build();
        String json = JukeConfigBuilder.toJSON(jc);
        assertNotNull(json);
        assertTrue(json.contains("className"));
    }

    @Test
    void fromJSON_roundTrip() throws Exception {
        JukeClass jc = JukeConfigBuilder.set(ISampleService.class).build();
        String json = JukeConfigBuilder.toJSON(jc);
        JukeClass restored = JukeConfigBuilder.fromJSON(json);
        assertNotNull(restored);
        assertEquals(jc.getClassName(), restored.getClassName());
    }

    // ── setJukeMethods ────────────────────────────────────────────────────

    @Test
    void setJukeMethods_excludesObjectMethods() {
        JukeConfigBuilder builder = new JukeConfigBuilder(ISampleService.class);
        List<JukeMethod> methods = builder.setJukeMethods(ISampleService.class);
        // toString/hashCode/equals must not be in the list
        boolean hasToString = methods.stream().anyMatch(m -> "toString".equals(m.getMethod()));
        assertFalse(hasToString);
    }

    @Test
    void setJukeMethods_includesServiceMethods() {
        JukeConfigBuilder builder = new JukeConfigBuilder(ISampleService.class);
        List<JukeMethod> methods = builder.setJukeMethods(ISampleService.class);
        boolean hasGetMyDataMap = methods.stream().anyMatch(m -> "getMyDataMap".equals(m.getMethod()));
        assertTrue(hasGetMyDataMap);
    }

    // ── constructParameterizedType (ParameterizedType) ────────────────────

    @Test
    void constructParameterizedType_mapType_noThrow() throws Exception {
        // Grab Map<String, BigDecimal[]> return type from getMyDataMap
        java.lang.reflect.Method m = ISampleService.class.getMethod(
                "getMyDataMap", java.math.BigDecimal[].class, String.class);
        java.lang.reflect.Type returnType = m.getGenericReturnType();
        if (returnType instanceof java.lang.reflect.ParameterizedType) {
            assertDoesNotThrow(() ->
                    JukeConfigBuilder.constructParameterizedType(
                            (java.lang.reflect.ParameterizedType) returnType));
        }
    }

    // ── constructType (JukeParameter) ─────────────────────────────────────

    @Test
    void constructType_simpleClassName_returnsJavaType() throws Exception {
        JukeParameter p = new JukeParameter();
        p.setClassName("java.lang.String");
        JavaType jt = JukeConfigBuilder.constructType(p);
        assertNotNull(jt);
        assertEquals(String.class, jt.getRawClass());
    }

    @Test
    void constructType_arrayParam_returnsArrayType() throws Exception {
        JukeParameter p = new JukeParameter();
        p.setArray(true);
        p.setClassName("java.lang.String");
        JavaType jt = JukeConfigBuilder.constructType(p);
        assertNotNull(jt);
        assertTrue(jt.isArrayType());
    }
}

