package org.juke.framework.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Marshaller} – covers getMapper and setMapper.
 */
class MarshallerTest {

    @Test
    void getMapper_returnsNonNull() {
        assertNotNull(Marshaller.getMapper());
    }

    @Test
    void setMapper_replacesMapper() {
        ObjectMapper original = Marshaller.getMapper();
        ObjectMapper custom = new ObjectMapper();
        try {
            Marshaller.setMapper(custom);
            assertSame(custom, Marshaller.getMapper());
        } finally {
            Marshaller.setMapper(original);
        }
    }

    @Test
    void getMapper_canSerializeSimpleObject() throws Exception {
        ObjectMapper mapper = Marshaller.getMapper();
        String json = mapper.writeValueAsString("hello");
        assertEquals("\"hello\"", json);
    }
}

