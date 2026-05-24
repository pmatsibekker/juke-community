package org.juke.framework.config;

import org.juke.framework.storage.JukeStorage;
import org.juke.framework.storage.JukeZipDAOImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Phase 5.A — Spring auto-configuration that registers the default
 * folder-backed {@link JukeStorage} bean per MIGRATION_PLAN.md §6.
 *
 * <p>The bean is gated on {@link ConditionalOnMissingBean} so a host
 * application can override it with a custom {@link JukeStorage}
 * implementation (e.g. the future {@code JukeJpaStorageBackend}
 * introduced in §5.C) and have the host's bean win without any
 * exclusion plumbing. Folder root comes from
 * {@link JukeStorageProperties#getPath() juke.storage.folder.path}.
 *
 * <p>The bean is folder-rooted: it can answer the recording-store API
 * ({@link JukeStorage#listRecordings},
 * {@link JukeStorage#loadRecording},
 * {@link JukeStorage#saveRecording}) but is not bound to a specific
 * recording for the per-recording API. Per-session usage materialises
 * its own {@link JukeZipDAOImpl} against a temp file from
 * {@link JukeStorage#loadRecording} and is unaffected.
 */
@AutoConfiguration
@EnableConfigurationProperties(JukeStorageProperties.class)
@ConditionalOnProperty(name = "juke.enabled", havingValue = "true")
public class JukeStorageAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(JukeStorageAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(JukeStorage.class)
    public JukeStorage jukeStorage(JukeStorageProperties props) {
        LOG.info("Registering folder-backed JukeStorage rooted at '{}'", props.getPath());
        return new JukeZipDAOImpl(props.getPath());
    }
}
