package org.juke.framework.metadata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PrimitiveReflectUtil}.
 */
class PrimitiveReflectUtilTest {

    @Test
    void isPrimitiveWrapperOf_intAndInteger_returnsTrue() {
        assertTrue(PrimitiveReflectUtil.isPrimitiveWrapperOf(Integer.class, int.class));
    }

    @Test
    void isPrimitiveWrapperOf_doubleAndDouble_returnsTrue() {
        assertTrue(PrimitiveReflectUtil.isPrimitiveWrapperOf(Double.class, double.class));
    }

    @Test
    void isPrimitiveWrapperOf_intAndLong_returnsFalse() {
        assertFalse(PrimitiveReflectUtil.isPrimitiveWrapperOf(Long.class, int.class));
    }

    @Test
    void isPrimitiveWrapperOf_nonPrimitive_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> PrimitiveReflectUtil.isPrimitiveWrapperOf(Integer.class, String.class));
    }

    @Test
    void isAssignableTo_sameClass_returnsTrue() {
        assertTrue(PrimitiveReflectUtil.isAssignableTo(String.class, Object.class));
    }

    @Test
    void isAssignableTo_primitiveFromWrapper_returnsTrue() {
        // from is primitive (int), to is wrapper (Integer)
        assertTrue(PrimitiveReflectUtil.isAssignableTo(int.class, Integer.class));
    }

    @Test
    void isAssignableTo_wrapperToPrimitive_returnsTrue() {
        // from is wrapper (Double), to is primitive (double)
        assertTrue(PrimitiveReflectUtil.isAssignableTo(Double.class, double.class));
    }

    @Test
    void isAssignableTo_unrelatedClasses_returnsFalse() {
        assertFalse(PrimitiveReflectUtil.isAssignableTo(String.class, Integer.class));
    }

    @Test
    void isAssignableTo_allPrimitiveTypes() {
        assertTrue(PrimitiveReflectUtil.isAssignableTo(boolean.class, Boolean.class));
        assertTrue(PrimitiveReflectUtil.isAssignableTo(byte.class, Byte.class));
        assertTrue(PrimitiveReflectUtil.isAssignableTo(char.class, Character.class));
        assertTrue(PrimitiveReflectUtil.isAssignableTo(float.class, Float.class));
        assertTrue(PrimitiveReflectUtil.isAssignableTo(long.class, Long.class));
        assertTrue(PrimitiveReflectUtil.isAssignableTo(short.class, Short.class));
    }
}

