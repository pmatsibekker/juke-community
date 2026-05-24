package org.juke.framework.metadata;

import com.fasterxml.jackson.databind.JavaType;
import org.juke.framework.support.ISampleService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JukeParameterizedTypeBuilder}.
 */
class JukeParameterizedTypeBuilderTest {

    // ── isArray ───────────────────────────────────────────────────────────

    @Test
    void isArray_arrayType_returnsTrue() {
        assertTrue(JukeParameterizedTypeBuilder.isArray("java.lang.String[]"));
    }

    @Test
    void isArray_nonArray_returnsFalse() {
        assertFalse(JukeParameterizedTypeBuilder.isArray("java.lang.String"));
    }

    // ── cleanArray ────────────────────────────────────────────────────────

    @Test
    void cleanArray_removesArraySuffix() {
        assertEquals("java.lang.String", JukeParameterizedTypeBuilder.cleanArray("java.lang.String[]"));
    }

    @Test
    void cleanArray_nonArray_returnsUnchanged() {
        assertEquals("java.lang.String", JukeParameterizedTypeBuilder.cleanArray("java.lang.String"));
    }

    // ── parseType ─────────────────────────────────────────────────────────

    @Test
    void parseType_boolean() throws Exception { assertEquals(boolean.class, JukeParameterizedTypeBuilder.parseType("boolean")); }
    @Test
    void parseType_byte() throws Exception { assertEquals(byte.class, JukeParameterizedTypeBuilder.parseType("byte")); }
    @Test
    void parseType_short() throws Exception { assertEquals(short.class, JukeParameterizedTypeBuilder.parseType("short")); }
    @Test
    void parseType_int() throws Exception { assertEquals(int.class, JukeParameterizedTypeBuilder.parseType("int")); }
    @Test
    void parseType_long() throws Exception { assertEquals(long.class, JukeParameterizedTypeBuilder.parseType("long")); }
    @Test
    void parseType_float() throws Exception { assertEquals(float.class, JukeParameterizedTypeBuilder.parseType("float")); }
    @Test
    void parseType_double() throws Exception { assertEquals(double.class, JukeParameterizedTypeBuilder.parseType("double")); }
    @Test
    void parseType_char() throws Exception { assertEquals(char.class, JukeParameterizedTypeBuilder.parseType("char")); }
    @Test
    void parseType_void() throws Exception { assertEquals(void.class, JukeParameterizedTypeBuilder.parseType("void")); }

    @Test
    void parseType_simpleClassName_addsJavaLangPrefix() throws Exception {
        assertEquals(String.class, JukeParameterizedTypeBuilder.parseType("String"));
    }

    @Test
    void parseType_fqn_returnsClass() throws Exception {
        assertEquals(java.math.BigDecimal.class, JukeParameterizedTypeBuilder.parseType("java.math.BigDecimal"));
    }

    @Test
    void parseType_unknownClass_throwsClassNotFoundException() {
        assertThrows(ClassNotFoundException.class,
                () -> JukeParameterizedTypeBuilder.parseType("com.nonexistent.Clazz"));
    }

    // ── fromParameterizedType: non-parameterized type ─────────────────────

    @Test
    void fromParameterizedType_simpleType_returnsWithRawType() throws Exception {
        Method m = ISampleService.class.getMethod("fromSimpleDoubleArray", double[].class);
        Type returnType = m.getGenericReturnType(); // Double[]
        JukeParameterizedType result = JukeParameterizedTypeBuilder.fromParameterizedType(returnType);
        assertNotNull(result);
        assertNotNull(result.getRawType());
    }

    @Test
    void fromParameterizedType_arrayType_setsArrayFlag() throws Exception {
        // fromSimpleDoubleArray returns Double[]
        Method m = ISampleService.class.getMethod("fromSimpleDoubleArray", double[].class);
        Type returnType = m.getGenericReturnType();
        JukeParameterizedType result = JukeParameterizedTypeBuilder.fromParameterizedType(returnType);
        // Double[] is an array type
        assertTrue(result.isArray());
    }

    @Test
    void fromParameterizedType_parameterizedType_setsTypeArguments() throws Exception {
        // getMyDataMap returns HashMap<String, BigDecimal>
        Method m = ISampleService.class.getMethod("getMyDataMap", BigDecimal[].class, String.class);
        Type returnType = m.getGenericReturnType();
        assertTrue(returnType instanceof ParameterizedType);
        JukeParameterizedType result = JukeParameterizedTypeBuilder.fromParameterizedType(returnType);
        assertNotNull(result.getRawType());
        assertFalse(result.getActualTypeArguments().isEmpty());
    }

    @Test
    void fromParameterizedType_nestedParameterizedType_handlesRecursion() throws Exception {
        // getMyDataMapAsList returns List<HashMap<String,BigDecimal>> — nested parameterized
        Method m = ISampleService.class.getMethod("getMyDataMapAsList", BigDecimal[].class, String.class);
        Type returnType = m.getGenericReturnType();
        assertTrue(returnType instanceof ParameterizedType);
        assertDoesNotThrow(() -> JukeParameterizedTypeBuilder.fromParameterizedType(returnType));
    }

    // ── fromJukeParameterizedType ─────────────────────────────────────────

    @Test
    void fromJukeParameterizedType_noArgs_returnsSimpleType() throws Exception {
        JukeParameterizedType pt = new JukeParameterizedType();
        pt.setRawType("java.lang.String");
        JavaType jt = JukeParameterizedTypeBuilder.fromJukeParameterizedType(pt);
        assertNotNull(jt);
        assertEquals(String.class, jt.getRawClass());
    }

    @Test
    void fromJukeParameterizedType_arrayType_returnsArrayJavaType() throws Exception {
        JukeParameterizedType pt = new JukeParameterizedType();
        pt.setRawType("java.lang.String");
        pt.setArray(true);
        JavaType jt = JukeParameterizedTypeBuilder.fromJukeParameterizedType(pt);
        assertNotNull(jt);
        assertTrue(jt.isArrayType());
    }

    @Test
    void fromJukeParameterizedType_withSimpleArgs_returnsParametricType() throws Exception {
        JukeParameterizedType pt = new JukeParameterizedType();
        pt.setRawType("java.util.List");
        JukeParameterizedType arg = new JukeParameterizedType();
        arg.setRawType("java.lang.String");
        pt.getActualTypeArguments().add(arg);
        JavaType jt = JukeParameterizedTypeBuilder.fromJukeParameterizedType(pt);
        assertNotNull(jt);
        assertEquals(List.class, jt.getRawClass());
    }

    @Test
    void fromJukeParameterizedType_withNestedArgs_returnsParametricType() throws Exception {
        // List<List<String>>
        JukeParameterizedType innerArg = new JukeParameterizedType();
        innerArg.setRawType("java.lang.String");

        JukeParameterizedType inner = new JukeParameterizedType();
        inner.setRawType("java.util.List");
        inner.getActualTypeArguments().add(innerArg);

        JukeParameterizedType outer = new JukeParameterizedType();
        outer.setRawType("java.util.List");
        outer.getActualTypeArguments().add(inner);

        JavaType jt = JukeParameterizedTypeBuilder.fromJukeParameterizedType(outer);
        assertNotNull(jt);
    }

    // ── JukeParameterizedType getters/setters ─────────────────────────────

    @Test
    void jukeParameterizedType_gettersSetters() {
        JukeParameterizedType pt = new JukeParameterizedType();
        pt.setRawType("com.example.Foo");
        pt.setParameterized(true);
        pt.setArray(true);
        pt.setActualTypeArguments(new ArrayList<>());

        assertEquals("com.example.Foo", pt.getRawType());
        assertTrue(pt.isParameterized());
        assertTrue(pt.isArray());
        assertNotNull(pt.getActualTypeArguments());
    }
}

