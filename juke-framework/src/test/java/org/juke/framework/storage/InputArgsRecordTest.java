package org.juke.framework.storage;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InputArgsRecord} – covers constructor, all getters, and toString.
 */
class InputArgsRecordTest {

    @Test
    void constructor_and_getters() {
        List<String> params = Arrays.asList("java.lang.String", "int");
        List<Object> args   = Arrays.asList("hello", 42);

        InputArgsRecord record = new InputArgsRecord("myMethod", params, args);

        assertEquals("myMethod", record.getMethod());
        assertEquals(params, record.getParameterTypes());
        assertEquals(args,   record.getArguments());
    }

    @Test
    void toString_containsMethod() {
        InputArgsRecord record = new InputArgsRecord("doWork",
                Collections.singletonList("java.lang.String"),
                Collections.singletonList("value"));

        String str = record.toString();
        assertTrue(str.contains("doWork"), "toString should contain method name");
        assertTrue(str.contains("java.lang.String"), "toString should contain param type");
    }

    @Test
    void constructor_withNullLists_isAllowed() {
        InputArgsRecord record = new InputArgsRecord("noArgs", null, null);
        assertEquals("noArgs", record.getMethod());
        assertNull(record.getParameterTypes());
        assertNull(record.getArguments());
    }

    @Test
    void toString_withNullLists_doesNotThrow() {
        InputArgsRecord record = new InputArgsRecord("nullArgs", null, null);
        assertDoesNotThrow(() -> {
            String s = record.toString();
            assertNotNull(s);
        });
    }
}

