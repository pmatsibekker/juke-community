package org.juke.plugin.sdk.lifecycle;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Tiny shared mutable holder for the plugin token + registration id obtained at registration
 * time. {@link PluginRegistrationLifecycle} writes; {@link HeartbeatPublisher} reads.
 *
 * <p>Spring scope is singleton — a plugin process is one plugin, and one plugin holds at most
 * one valid token at any moment. The {@link AtomicReference} guard is paranoia rather than a
 * real concurrency requirement: registration runs on the {@code ApplicationReadyEvent} thread,
 * heartbeats on the scheduler thread, so token visibility across threads matters even though
 * neither writes concurrently.
 */
public class PluginRegistrationState {

    private final AtomicReference<String> token = new AtomicReference<>();
    private final AtomicReference<String> registrationId = new AtomicReference<>();

    public String getToken() { return token.get(); }
    public void setToken(String value) { this.token.set(value); }
    public boolean hasToken() { return token.get() != null; }

    public String getRegistrationId() { return registrationId.get(); }
    public void setRegistrationId(String value) { this.registrationId.set(value); }

    public void clear() {
        this.token.set(null);
        this.registrationId.set(null);
    }
}
