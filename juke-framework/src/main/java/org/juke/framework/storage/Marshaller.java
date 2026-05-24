package org.juke.framework.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
public  class Marshaller {

    /**
     * Phase 5.5 — declared {@code volatile} so the test-only
     * {@link #setMapper(ObjectMapper)} setter has a happens-before
     * relationship with subsequent {@link #getMapper()} reads from any
     * other thread. Production never reassigns the reference; the only
     * known caller of {@code setMapper} is {@code MarshallerTest} which
     * swaps and restores it for isolation.
     */
    private static volatile ObjectMapper objectMapper = new ObjectMapper();
    static {
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.disable(SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

    }


    public static ObjectMapper getMapper() {
        return objectMapper;
    }

    public static void setMapper(ObjectMapper mapper) {
       objectMapper = mapper;
    }

}