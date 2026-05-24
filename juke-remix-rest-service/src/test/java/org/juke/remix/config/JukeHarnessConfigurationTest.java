package org.juke.remix.config;

import org.juke.framework.harness.NoOpUiHarness;
import org.juke.framework.harness.UiArtefactDescriptor;
import org.juke.framework.harness.UiHarness;
import org.juke.framework.harness.UseCaseExecutionPlan;
import org.juke.framework.session.JukeSessionContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5.B simplification: this test used to stub a
 * {@code BundleBackedSessionResolver} so the {@link JukeHarnessConfiguration}
 * context could instantiate its now-relocated {@code jukeSessionRegistry}
 * {@code @Bean}. With the bundle-backed session wiring split out into
 * {@code org.juke.scenario.config.BundleSessionConfiguration}, this
 * configuration is purely about UiHarness election — no scenario-side
 * dependencies remain.
 */
class JukeHarnessConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(JukeHarnessConfiguration.class);

    @Test
    void defaultIsNoOpHarnessWhenPropertyUnset() {
        runner.withBean(NoOpUiHarness.class).run(ctx -> {
            UiHarness active = ctx.getBean(UiHarness.class);
            assertThat(active.id()).isEqualTo("none");
            assertThat(active).isInstanceOf(NoOpUiHarness.class);
        });
    }

    @Test
    void electsNamedHarnessWhenPropertyMatches() {
        runner.withPropertyValues("juke.ui-harness=playwright")
                .withBean(NoOpUiHarness.class)
                .withUserConfiguration(FakePlaywrightConfig.class)
                .run(ctx -> {
                    UiHarness active = ctx.getBean(UiHarness.class);
                    assertThat(active.id()).isEqualTo("playwright");
                });
    }

    @Test
    void failsFastWhenNamedHarnessIsMissing() {
        runner.withPropertyValues("juke.ui-harness=cypress")
                .withBean(NoOpUiHarness.class)
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure()
                        .hasMessageContaining("juke.ui-harness=cypress")
                        .hasMessageContaining("Available harnesses"));
    }

    @SpringBootConfiguration
    @Configuration
    static class FakePlaywrightConfig {
        @Bean
        UiHarness fakePlaywright() {
            return new UiHarness() {
                @Override public String id() { return "playwright"; }
                @Override public UiArtefactDescriptor describeArtefact() {
                    return UiArtefactDescriptor.zip("playwright", "Playwright trace");
                }
                @Override public void onSessionStart(JukeSessionContext ctx) { }
                @Override public Optional<byte[]> onSessionStop(JukeSessionContext ctx) {
                    return Optional.empty();
                }
                @Override public boolean canExecuteRecording() { return true; }
                @Override public void executeUseCase(UseCaseExecutionPlan plan) { }
            };
        }
    }
}
