package org.juke.framework.proxy;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers uncovered branches in {@link TypeDiscriminatorUtil}.
 */
class TypeDiscriminatorUtilTest {

    // helper interface with a Class<?> parameter
    interface WithClassArg {
        String fetch(Class<?> type, String id);
    }

    // helper interface with no Class<?> parameter
    interface WithoutClassArg {
        String fetch(String id);
    }

    // ── extractTypeDiscriminator ───────────────────────────────────────────

    @Test
    void extractTypeDiscriminator_nullMethod_returnsNull() {
        assertNull(TypeDiscriminatorUtil.extractTypeDiscriminator(null, new Object[]{"a"}));
    }

    @Test
    void extractTypeDiscriminator_nullArgs_returnsNull() throws Exception {
        Method m = WithClassArg.class.getMethod("fetch", Class.class, String.class);
        assertNull(TypeDiscriminatorUtil.extractTypeDiscriminator(m, null));
    }

    @Test
    void extractTypeDiscriminator_withClassArg_returnsName() throws Exception {
        Method m = WithClassArg.class.getMethod("fetch", Class.class, String.class);
        String result = TypeDiscriminatorUtil.extractTypeDiscriminator(m, new Object[]{String.class, "id"});
        assertEquals(String.class.getCanonicalName(), result);
    }

    @Test
    void extractTypeDiscriminator_withoutClassArg_returnsNull() throws Exception {
        Method m = WithoutClassArg.class.getMethod("fetch", String.class);
        assertNull(TypeDiscriminatorUtil.extractTypeDiscriminator(m, new Object[]{"id"}));
    }

    // ── extractTypeDiscriminatorClass ─────────────────────────────────────

    @Test
    void extractTypeDiscriminatorClass_nullMethod_returnsNull() {
        assertNull(TypeDiscriminatorUtil.extractTypeDiscriminatorClass(null, new Object[]{}));
    }

    @Test
    void extractTypeDiscriminatorClass_nullArgs_returnsNull() throws Exception {
        Method m = WithClassArg.class.getMethod("fetch", Class.class, String.class);
        assertNull(TypeDiscriminatorUtil.extractTypeDiscriminatorClass(m, null));
    }

    @Test
    void extractTypeDiscriminatorClass_withClassArg_returnsClass() throws Exception {
        Method m = WithClassArg.class.getMethod("fetch", Class.class, String.class);
        Class<?> result = TypeDiscriminatorUtil.extractTypeDiscriminatorClass(m, new Object[]{Integer.class, "x"});
        assertSame(Integer.class, result);
    }

    @Test
    void extractTypeDiscriminatorClass_withoutClassArg_returnsNull() throws Exception {
        Method m = WithoutClassArg.class.getMethod("fetch", String.class);
        assertNull(TypeDiscriminatorUtil.extractTypeDiscriminatorClass(m, new Object[]{"id"}));
    }

    // ── buildRecordIdentifier ─────────────────────────────────────────────

    @Test
    void buildRecordIdentifier_nullDiscriminator_returnsBase() {
        assertEquals("com.Foo.$bar", TypeDiscriminatorUtil.buildRecordIdentifier("com.Foo.$bar", null));
    }

    @Test
    void buildRecordIdentifier_emptyDiscriminator_returnsBase() {
        assertEquals("com.Foo.$bar", TypeDiscriminatorUtil.buildRecordIdentifier("com.Foo.$bar", ""));
    }

    @Test
    void buildRecordIdentifier_withDiscriminator_appendsWithAt() {
        String result = TypeDiscriminatorUtil.buildRecordIdentifier("com.Foo.$bar", "com.example.Result");
        assertEquals("com.Foo.$bar@com.example.Result", result);
    }

    // ── hasTypeDiscriminator ──────────────────────────────────────────────

    @Test
    void hasTypeDiscriminator_withAt_returnsTrue() {
        assertTrue(TypeDiscriminatorUtil.hasTypeDiscriminator("com.Foo.$bar@com.Result.1.json"));
    }

    @Test
    void hasTypeDiscriminator_withoutAt_returnsFalse() {
        assertFalse(TypeDiscriminatorUtil.hasTypeDiscriminator("com.Foo.$bar.1.json"));
    }

    @Test
    void hasTypeDiscriminator_null_returnsFalse() {
        assertFalse(TypeDiscriminatorUtil.hasTypeDiscriminator(null));
    }

    // ── getDiscriminatorFromEntryName ─────────────────────────────────────

    @Test
    void getDiscriminatorFromEntryName_null_returnsNull() {
        assertNull(TypeDiscriminatorUtil.getDiscriminatorFromEntryName(null));
    }

    @Test
    void getDiscriminatorFromEntryName_noAt_returnsNull() {
        assertNull(TypeDiscriminatorUtil.getDiscriminatorFromEntryName("com.Foo.$bar.1.json"));
    }

    @Test
    void getDiscriminatorFromEntryName_withNumericSuffix_returnsClassName() {
        // e.g. "com.Foo.$bar@com.example.Result.1.json"
        String result = TypeDiscriminatorUtil.getDiscriminatorFromEntryName(
                "com.Foo.$bar@com.example.Result.1.json");
        assertEquals("com.example.Result", result);
    }

    @Test
    void getDiscriminatorFromEntryName_withOnlyAt_returnsAfterAt() {
        // Edge case: no dots after @
        String result = TypeDiscriminatorUtil.getDiscriminatorFromEntryName("base@SomeType");
        assertEquals("SomeType", result);
    }

    @Test
    void getDiscriminatorFromEntryName_simpleDiscriminatorWithDotJson_returnsType() {
        // "base@Result.json" -> dotBeforeSeq > 0, candidate="Result", dotBeforeNum=-1 -> returns "Result"
        String result = TypeDiscriminatorUtil.getDiscriminatorFromEntryName("base@Result.json");
        assertEquals("Result", result);
    }

    @Test
    void getDiscriminatorFromEntryName_nonNumericLastSegment_returnsCandidate() {
        // "base@com.example.Result.json" -> dotBeforeSeq removes ".json",
        // candidate="com.example.Result", dotBeforeNum>0, numPart="Result" -> not digit -> returns candidate
        String result = TypeDiscriminatorUtil.getDiscriminatorFromEntryName("base@com.example.Result.json");
        assertEquals("com.example.Result", result);
    }
}

