package org.juke.remix.plugin;

import org.juke.plugin.api.PluginCapability;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Resolves the {@link RegisteredPlugin} that should service a capability call. Takes a
 * capability and an optional explicit {@code pluginId}; when {@code pluginId == null} returns
 * the first ACTIVE plugin advertising the capability.
 *
 * <p>Centralised here so {@link PluginInvoker} and any in-process consumer
 * (validation pipeline, controller-replay step, etc.) make the same choice.
 */
@Component
public class CapabilityResolver {

    private final PluginRegistry registry;

    public CapabilityResolver(PluginRegistry registry) {
        this.registry = registry;
    }

    public boolean hasActive(PluginCapability capability) {
        return !registry.activeFor(capability).isEmpty();
    }

    public List<RegisteredPlugin> listActive(PluginCapability capability) {
        return registry.activeFor(capability);
    }

    /**
     * @param capability the capability category to resolve for
     * @param pluginId explicit plugin id, or {@code null} to mean "any active plugin"
     * @return the resolved plugin, or {@link Optional#empty()} when no active plugin advertises
     *         the capability (or the explicitly named plugin isn't active)
     */
    public Optional<RegisteredPlugin> resolve(PluginCapability capability, String pluginId) {
        if (pluginId != null) {
            return registry.findById(pluginId)
                    .filter(p -> p.capabilityCategories().contains(capability));
        }
        List<RegisteredPlugin> active = registry.activeFor(capability);
        return active.isEmpty() ? Optional.empty() : Optional.of(active.get(0));
    }
}
