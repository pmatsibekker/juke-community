package org.juke.framework.validation;

import org.juke.framework.annotation.JukeIgnorable;
import org.juke.framework.annotation.JukeIgnorable.IgnoreStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JukeIgnorableScanner} — confirms it produces
 * InputDiffEngine-style json paths for {@link JukeIgnorable} fields, including
 * nested POJOs, and ignores JDK types and collections.
 */
class JukeIgnorableScannerTest {

    static class OrderRequest {
        public String sku;
        @JukeIgnorable
        public String confirmationNumber;
        @JukeIgnorable(strategy = IgnoreStrategy.NOT_NULL)
        public String submittedAt;
        public Customer customer;
    }

    static class Customer {
        public String name;
        @JukeIgnorable
        public String sessionToken;
    }

    @Test
    void scansTopLevelAndNestedIgnorableFields() {
        OrderRequest req = new OrderRequest();
        req.customer = new Customer();

        List<FieldIgnoreRule> rules = JukeIgnorableScanner.scanArgs(new Object[]{ req });
        Map<String, IgnoreStrategy> byPath = rules.stream()
                .collect(Collectors.toMap(FieldIgnoreRule::jsonPath, FieldIgnoreRule::strategy));

        assertEquals(IgnoreStrategy.ALWAYS,   byPath.get("$[0].confirmationNumber"));
        assertEquals(IgnoreStrategy.NOT_NULL, byPath.get("$[0].submittedAt"));
        assertEquals(IgnoreStrategy.ALWAYS,   byPath.get("$[0].customer.sessionToken"));
        // sku and customer.name are not ignorable
        assertFalse(byPath.containsKey("$[0].sku"));
        assertFalse(byPath.containsKey("$[0].customer.name"));
    }

    @Test
    void positionsRulesByArgumentIndex() {
        OrderRequest a = new OrderRequest();
        OrderRequest b = new OrderRequest();
        List<FieldIgnoreRule> rules = JukeIgnorableScanner.scanArgs(new Object[]{ "plain", a, b });

        List<String> paths = rules.stream().map(FieldIgnoreRule::jsonPath).collect(Collectors.toList());
        assertTrue(paths.contains("$[1].confirmationNumber"));
        assertTrue(paths.contains("$[2].confirmationNumber"));
        // The String arg at index 0 contributes nothing.
        assertTrue(paths.stream().noneMatch(p -> p.startsWith("$[0]")));
    }

    @Test
    void nullAndEmptyArgsAreSafe() {
        assertTrue(JukeIgnorableScanner.scanArgs(null).isEmpty());
        assertTrue(JukeIgnorableScanner.scanArgs(new Object[0]).isEmpty());
        assertTrue(JukeIgnorableScanner.scanArgs(new Object[]{ null, "x", 42 }).isEmpty());
    }
}
