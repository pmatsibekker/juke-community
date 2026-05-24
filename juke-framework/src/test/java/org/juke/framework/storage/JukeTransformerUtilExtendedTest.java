package org.juke.framework.storage;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.juke.framework.metadata.JukeClass;
import org.juke.framework.metadata.JukeConfigBuilder;
import org.juke.framework.support.ISampleService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the missing branches in {@link JukeTransformerUtil}:
 *  - readValue(String, JukeClass, String) where method is not found → throws Exception
 *  - readValue(String, JukeClass, String, JavaType) where method is not found → throws Exception
 *  - readValueAsType convenience overload
 *  - writeValueAsString
 */
class JukeTransformerUtilExtendedTest {

    private static JukeClass jukeClass;

    @BeforeAll
    static void buildJukeClass() throws Exception {
        jukeClass = JukeConfigBuilder.set(ISampleService.class).build();
    }

    @Test
    void readValue_methodNotFound_throwsException() {
        Exception ex = assertThrows(Exception.class,
                () -> JukeTransformerUtil.readValue("{}", jukeClass, "nonExistentMethod"));
        assertTrue(ex.getMessage().contains("nonExistentMethod"));
    }

    @Test
    void readValueWithJavaType_methodNotFound_throwsException() {
        JavaType jt = new ObjectMapper().constructType(String.class);
        Exception ex = assertThrows(Exception.class,
                () -> JukeTransformerUtil.readValue("{}", jukeClass, "nonExistentMethod2", jt));
        assertTrue(ex.getMessage().contains("nonExistentMethod2"));
    }

    @Test
    void readValueAsType_deserializesCorrectly() throws Exception {
        String json = "\"hello\"";
        String result = JukeTransformerUtil.readValueAsType(json, String.class);
        assertEquals("hello", result);
    }

    @Test
    void writeValueAsString_producesJson() throws Exception {
        String json = JukeTransformerUtil.writeValueAsString("test-value");
        assertNotNull(json);
        assertTrue(json.contains("test-value"));
    }
}

