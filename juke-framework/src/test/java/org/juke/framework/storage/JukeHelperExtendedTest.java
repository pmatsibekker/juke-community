package org.juke.framework.storage;

import org.juke.framework.proxy.JukeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JukeHelper} – covers validateInputArgs (off/warn/strict modes),
 * readInputArgs, writeTypeMetadata, writeInputArgs, and static accessors.
 */
class JukeHelperExtendedTest {

    @TempDir
    Path tempDir;

    private String savedGlobal;

    @BeforeEach
    void setUp() {
        savedGlobal = JukeState.getGlobaljuke();
        System.setProperty("juke.path", tempDir.toString());
        System.setProperty("juke.zip", "helper-test");
        JukeState.setGlobaljuke(JukeState.RECORD);
        JukeHelper.setJukeDao(new JukeZipDAOImpl(tempDir.toString(), "helper-test"));
    }

    @AfterEach
    void tearDown() {
        JukeState.setGlobaljuke(savedGlobal);
        System.clearProperty("juke.path");
        System.clearProperty("juke.zip");
        System.clearProperty("juke.args-validation");
    }

    // ---- static accessors ----

    @Test
    void getInstance_isNotNull() {
        assertNotNull(JukeHelper.getInstance());
    }

    @Test
    void getJukeDAO_isNotNull() {
        assertNotNull(JukeHelper.getJukeDAO());
    }

    @Test
    void setJukeDao_replacesDao() {
        JukeZipDAOImpl newDao = new JukeZipDAOImpl(tempDir.toString(), "helper-test2");
        JukeHelper.setJukeDao(newDao);
        assertSame(newDao, JukeHelper.getJukeDAO());
    }

    // ---- readInputArgs – missing entry returns null ----

    @Test
    void readInputArgs_missingEntry_returnsNull() {
        InputArgsRecord rec = JukeHelper.readInputArgs("NonExistent.method.99");
        assertNull(rec, "Missing .args entry should return null");
    }

    // ---- validateInputArgs – off mode does nothing ----

    @Test
    void validateInputArgs_offMode_doesNothing() throws Exception {
        System.setProperty("juke.args-validation", "off");
        Method m = String.class.getMethod("length");
        // Should not throw even with non-matching args
        assertDoesNotThrow(() ->
                JukeHelper.validateInputArgs("any.id.1", m, new Object[]{"unexpected"}));
    }

    // ---- validateInputArgs – no recorded args (no .args entry) is a no-op ----

    @Test
    void validateInputArgs_noRecordedArgs_isNoOp() throws Exception {
        System.setProperty("juke.args-validation", "warn");
        Method m = String.class.getMethod("length");
        assertDoesNotThrow(() ->
                JukeHelper.validateInputArgs("nonexistent.sig.1", m, null));
    }

    // ---- validateInputArgs – strict mode, args match → no exception ----

    @Test
    void validateInputArgs_strictMode_matchingArgs_noException() throws Exception {
        System.setProperty("juke.args-validation", "strict");

        // Write a matching .args entry
        String identifier = "MyService.doStuff";
        JukeHelper.getJukeDAO().writeToFile(identifier, "\"result\"");
        int seq = JukeHelper.getJukeDAO().getCurrentSequence(identifier);

        InputArgsRecord record = new InputArgsRecord(
                "doStuff",
                Collections.singletonList("java.lang.String"),
                Collections.singletonList("hello"));
        String argsJson = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(record);
        JukeHelper.getJukeDAO().writeDirectEntry(identifier + "." + seq + ".args.json", argsJson);

        Method m = String.class.getMethod("length");
        // args match exactly when serialized → no exception
        assertDoesNotThrow(() ->
                JukeHelper.validateInputArgs(identifier + "." + seq, m, new Object[]{"hello"}));
    }

    // ---- writeTypeMetadata and writeInputArgs do not throw ----

    @Test
    void writeTypeMetadata_doesNotThrow() throws Exception {
        String identifier = "TestService.run";
        JukeHelper.getJukeDAO().writeToFile(identifier, "\"ok\"");

        JukeHelper helper = JukeHelper.getInstance();
        assertDoesNotThrow(() -> helper.writeTypeMetadata(identifier, "java.lang.String"));
    }

    @Test
    void writeInputArgs_noArgs_doesNotThrow() throws Exception {
        String identifier = "TestService.noArgMethod";
        JukeHelper.getJukeDAO().writeToFile(identifier, "\"ok\"");

        Method m = Object.class.getMethod("toString");
        JukeHelper helper = JukeHelper.getInstance();
        assertDoesNotThrow(() -> helper.writeInputArgs(identifier, m, null));
    }

    @Test
    void write_doesNotThrow() {
        JukeHelper helper = JukeHelper.getInstance();
        assertDoesNotThrow(helper::write);
    }

    @Test
    void writeToFile_doesNotThrow() {
        // JukeZipDAOImpl.writeToFile always returns false (by design — entries are
        // buffered in-memory and only flushed on write()).  Just verify no exception.
        JukeHelper helper = JukeHelper.getInstance();
        assertDoesNotThrow(() -> helper.writeToFile("TestService.myMethod", "some-result-object"));
    }
}

