package org.juke.framework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binding target for {@code juke.storage.*} properties — currently the
 * folder-backed defaults. Phase 5.A per MIGRATION_PLAN.md §6: introduces
 * configurable folder root for the {@code JukeZipDAOImpl}-backed
 * {@code JukeStorage} bean. A future Phase 5.C may add a sibling
 * {@code juke.storage.backend = db} switch wired to a JPA-backed
 * implementation.
 *
 * <p>Property key {@code juke.storage.folder.path} matches the existing
 * configuration vocabulary (the Phase 0 audit noted {@code juke.path}
 * as the legacy system property; the YAML key normalises that).
 */
@ConfigurationProperties(prefix = "juke.storage.folder")
public class JukeStorageProperties {

    /**
     * Filesystem directory holding {@code *.zip} recordings. Defaults to
     * {@code recordings} (a relative path resolved against the host
     * application's working directory) so out-of-the-box wiring works
     * without explicit configuration.
     */
    private String path = "recordings";

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
}
