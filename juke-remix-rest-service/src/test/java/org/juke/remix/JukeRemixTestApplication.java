package org.juke.remix;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Test-only {@link SpringBootApplication} entry point for
 * {@code juke-remix-rest-service} integration tests.
 *
 * <p>The production app's {@code RemixBootstrapTestStarter} is annotated
 * {@code @Profile("dev")}, so {@link org.springframework.boot.test.context.SpringBootTest
 * @SpringBootTest}'s upward package search rejects it under any other
 * profile. This class lives at {@code org.juke.remix} so any test in the
 * {@code org.juke.remix.*} subtree picks it up automatically without
 * needing {@code @SpringBootTest(classes=...)} on every test.
 *
 * <h2>Phase 5.B simplification</h2>
 *
 * <p>This class previously excluded
 * {@code ScenarioServiceAutoConfiguration} and the entire
 * {@code org.juke.scenario.*} component scan because the now-deleted
 * Stack B Legacy entities ({@code @Entity(name = "Legacy*")}) collided
 * with their Stack A canonical counterparts on Hibernate's entity-name
 * registry. With §5.0 step 3 having dropped the Legacy entities and the
 * plural Stack B tables, that collision is gone — and the JPA-touching
 * controllers, services, and bundle resolver have all moved to
 * {@code juke-scenario-service} as part of §5.B. Component scan can now
 * safely cover the full {@code org.juke} tree (the scenario module's
 * {@code @AutoConfiguration} contributes its repository / entity scans
 * automatically), so this test entry point reduces to a stock
 * {@code @SpringBootApplication}.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"org.juke.remix"})
public class JukeRemixTestApplication {
}
