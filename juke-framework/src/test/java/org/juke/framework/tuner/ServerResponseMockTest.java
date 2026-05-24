package org.juke.framework.tuner;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ServerResponseMock} – covers getException for all registered
 * exception types, unregistered names (fallback to "Exception"), and null.
 */
class ServerResponseMockTest {

    @Test
    void getException_knownName_returnsCorrectType() {
        Exception e = ServerResponseMock.getException("NullPointerException");
        assertNotNull(e);
        assertTrue(e instanceof NullPointerException,
                "Expected NullPointerException, got: " + e.getClass());
    }

    @Test
    void getException_ioException_returnsIOException() {
        Exception e = ServerResponseMock.getException("IOException");
        assertNotNull(e);
        assertTrue(e instanceof IOException);
    }

    @Test
    void getException_illegalAccessException_returnsIllegalAccessException() {
        Exception e = ServerResponseMock.getException("IllegalAccessException");
        assertNotNull(e);
        assertTrue(e instanceof IllegalAccessException);
    }

    @Test
    void getException_unknownName_fallsBackToException() {
        Exception e = ServerResponseMock.getException("SomeRandomException");
        assertNotNull(e);
        assertEquals(Exception.class, e.getClass(),
                "Unknown name should fall back to generic Exception");
    }

    @Test
    void getException_nullName_fallsBackToException() {
        Exception e = ServerResponseMock.getException(null);
        assertNotNull(e);
        assertEquals(Exception.class, e.getClass(),
                "null name should fall back to generic Exception");
    }

    @Test
    void registerException_customException_canBeRetrieved() {
        RuntimeException custom = new RuntimeException("custom");
        ServerResponseMock.registerException("MyCustom", custom);
        assertSame(custom, ServerResponseMock.getException("MyCustom"));
    }

    @Test
    void constructor_throwsRegisteredExceptionForKnownType() {
        assertThrows(Exception.class,
                () -> new ServerResponseMock("NullPointerException", "{}"));
    }

    @Test
    void constructor_throwsGenericExceptionForUnknownType() {
        assertThrows(Exception.class,
                () -> new ServerResponseMock("UnknownException", "{}"));
    }
}

