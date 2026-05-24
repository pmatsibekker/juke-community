package org.juke.framework.proxy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Additional coverage for {@link JukeNameFormatter} – static/helper methods
 * and entry mapping lifecycle.
 */
class JukeNameFormatterTest {

    @BeforeEach
    @AfterEach
    void clearMappings() {
        JukeNameFormatter.clearMappings();
    }

    // ── simpleName ────────────────────────────────────────────────────────

    @Test
    void simpleName_qualifiedClass() {
        assertEquals("MyService", JukeNameFormatter.simpleName("com.example.MyService"));
    }

    @Test
    void simpleName_innerClass() {
        assertEquals("Inner", JukeNameFormatter.simpleName("com.example.Outer$Inner"));
    }

    @Test
    void simpleName_noPackage() {
        assertEquals("Bare", JukeNameFormatter.simpleName("Bare"));
    }

    @Test
    void simpleName_null_returnsEmpty() {
        assertEquals("", JukeNameFormatter.simpleName(null));
    }

    // ── cleanMethodName ───────────────────────────────────────────────────

    @Test
    void cleanMethodName_stripsLongHashSuffix() {
        // hash is >= 5 digits
        assertEquals("getData", JukeNameFormatter.cleanMethodName("getData12345"));
    }

    @Test
    void cleanMethodName_noHash_returnsUnchanged() {
        assertEquals("getData", JukeNameFormatter.cleanMethodName("getData"));
    }

    @Test
    void cleanMethodName_shortDigitSuffix_returnsUnchanged() {
        // 4 digits is not long enough to be a hash
        assertEquals("getData123", JukeNameFormatter.cleanMethodName("getData123"));
    }

    @Test
    void cleanMethodName_negativeHash_stripped() {
        assertEquals("getData", JukeNameFormatter.cleanMethodName("getData-12345"));
    }

    @Test
    void cleanMethodName_null_returnsEmpty() {
        assertEquals("", JukeNameFormatter.cleanMethodName(null));
    }

    // ── stripDollarPrefix ─────────────────────────────────────────────────

    @Test
    void stripDollarPrefix_leadingDollar() {
        assertEquals("method", JukeNameFormatter.stripDollarPrefix("$method"));
    }

    @Test
    void stripDollarPrefix_noDollar_unchanged() {
        assertEquals("method", JukeNameFormatter.stripDollarPrefix("method"));
    }

    // ── disambiguatedName / collision detection ───────────────────────────

    @Test
    void disambiguatedName_noCollision_returnsSimpleName() {
        JukeNameFormatter.registerFqn("com.example.MyService");
        assertEquals("MyService", JukeNameFormatter.disambiguatedName("com.example.MyService"));
    }

    @Test
    void disambiguatedName_collision_returnsMinDistinguishing() {
        JukeNameFormatter.registerFqn("com.example.MyService");
        JukeNameFormatter.registerFqn("com.other.MyService");

        String nameA = JukeNameFormatter.disambiguatedName("com.example.MyService");
        String nameB = JukeNameFormatter.disambiguatedName("com.other.MyService");

        assertNotEquals(nameA, nameB);
        assertTrue(nameA.contains("MyService"));
        assertTrue(nameB.contains("MyService"));
    }

    @Test
    void disambiguatedName_emptyFqn_returnsEmpty() {
        assertEquals("", JukeNameFormatter.disambiguatedName(""));
        assertEquals("", JukeNameFormatter.disambiguatedName(null));
    }

    // ── registerFqn null / empty ──────────────────────────────────────────

    @Test
    void registerFqn_nullAndEmpty_noException() {
        assertDoesNotThrow(() -> JukeNameFormatter.registerFqn(null));
        assertDoesNotThrow(() -> JukeNameFormatter.registerFqn(""));
    }

    // ── splitFqn / joinTail ───────────────────────────────────────────────

    @Test
    void splitFqn_dotSeparated() {
        String[] parts = JukeNameFormatter.splitFqn("com.example.MyService");
        assertArrayEquals(new String[]{"com", "example", "MyService"}, parts);
    }

    @Test
    void splitFqn_dollarSeparated() {
        String[] parts = JukeNameFormatter.splitFqn("Outer$Inner");
        assertArrayEquals(new String[]{"Outer", "Inner"}, parts);
    }

    @Test
    void joinTail_lastTwo() {
        assertEquals("api.MyService",
                JukeNameFormatter.joinTail(new String[]{"com", "example", "api", "MyService"}, 2));
    }

    @Test
    void joinTail_countExceedsParts_returnsAll() {
        assertEquals("com.example",
                JukeNameFormatter.joinTail(new String[]{"com", "example"}, 10));
    }

    // ── buildShortIdentifier ──────────────────────────────────────────────

    @Test
    void buildShortIdentifier_withoutDiscriminator() {
        JukeNameFormatter.registerFqn("org.juke.framework.support.ISampleService");
        String id = JukeNameFormatter.buildShortIdentifier(
                org.juke.framework.support.ISampleService.class,
                "getMyDataMap",
                null);
        assertEquals("ISampleService.getMyDataMap", id);
    }

    @Test
    void buildShortIdentifier_withDiscriminator() {
        String id = JukeNameFormatter.buildShortIdentifier(
                org.juke.framework.support.ISampleService.class,
                "getMyDataMap",
                "java.lang.String");
        assertTrue(id.contains("@String"));
    }

    // ── getEntryMappings after clear ──────────────────────────────────────
    @Test
    void getEntryMappings_afterClear_isEmpty() {
        JukeNameFormatter.clearMappings();
        assertTrue(JukeNameFormatter.getEntryMappings().isEmpty());
    }

    // ── buildAndRegister ──────────────────────────────────────────────────

    @Test
    void buildAndRegister_withoutDiscriminator_registersMapping() throws Exception {
        java.lang.reflect.Method m = org.juke.framework.support.ISampleService.class
                .getMethod("getMyDataMap", java.math.BigDecimal[].class, String.class);
        String id = JukeNameFormatter.buildAndRegister(
                org.juke.framework.support.ISampleService.class, m, "getMyDataMap", null);
        assertNotNull(id);
        assertFalse(JukeNameFormatter.getEntryMappings().isEmpty());
    }

    @Test
    void buildAndRegister_withDiscriminator_includesTypeInId() throws Exception {
        java.lang.reflect.Method m = org.juke.framework.support.ISampleService.class
                .getMethod("getMyDataMap", java.math.BigDecimal[].class, String.class);
        String id = JukeNameFormatter.buildAndRegister(
                org.juke.framework.support.ISampleService.class, m, "getMyDataMap", "java.lang.String");
        assertNotNull(id);
        assertTrue(id.contains("@"));
    }

    // ── buildHumanSignature covers multi-param method ─────────────────────

    @Test
    void buildShortIdentifier_withDiscriminator_coversBuildHumanSignatureMultiParam() throws Exception {
        java.lang.reflect.Method m = org.juke.framework.support.ISampleService.class
                .getMethod("getMyDataMap", java.math.BigDecimal[].class, String.class);
        // call buildAndRegister to exercise buildHumanSignature with multiple params
        String id = JukeNameFormatter.buildAndRegister(
                org.juke.framework.support.ISampleService.class, m, "getMyDataMap", "java.lang.String");
        JukeNameFormatter.EntryMapping mapping = JukeNameFormatter.getEntryMappings().get(id);
        assertNotNull(mapping);
        assertNotNull(mapping.getSignature());
        assertTrue(mapping.getSignature().contains("getMyDataMap"));
    }
}

