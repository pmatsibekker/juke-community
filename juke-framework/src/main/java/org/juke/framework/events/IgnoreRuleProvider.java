package org.juke.framework.events;

import org.juke.framework.validation.FieldIgnoreRule;

import java.util.Collections;
import java.util.List;

/**
 * Framework-owned read channel for {@link FieldIgnoreRule}s. The scenario
 * service registers an implementation via
 * {@link ScenarioEvents#setIgnoreRuleProvider(IgnoreRuleProvider)} that
 * queries the {@code field_ignore_rule} table.
 *
 * <p>Ordering is deferred to the implementation. The conventional layering
 * is: ANNOTATION rules first (auto-seeded), then MANUAL rules — so MANUAL
 * overrides for the same {@code jsonPath} win in {@link
 * org.juke.framework.validation.InputDiffEngine}'s last-write-wins indexing.
 */
public interface IgnoreRuleProvider {

    /**
     * Resolve the active ignore rules for the given replay call site.
     *
     * @param className  FQCN of the class being replayed
     * @param methodName method being replayed
     * @return list of rules (never null); empty when nothing applies
     */
    List<FieldIgnoreRule> findFor(String className, String methodName);

    /** Empty default — used when the scenario service hasn't registered. */
    IgnoreRuleProvider EMPTY = (className, methodName) -> Collections.emptyList();
}
