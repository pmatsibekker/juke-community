package org.juke.plugin.sdk.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level annotation declaring that a Spring bean implements a single Juke plugin
 * capability. The SDK collects every bean carrying this annotation, walks its
 * {@link PluginEndpoint}-annotated methods, and surfaces a
 * {@link org.juke.plugin.api.registration.CapabilityDescriptor} in the registration payload.
 *
 * <p>The annotation is meta-annotated with {@link Component}, so plugin authors can declare a
 * capability bean without an extra {@code @Component} on the same class.
 *
 * <p>The annotation references {@link org.juke.plugin.api.PluginCapability} via {@link #value}
 * so the wire enum and the SDK annotation can never drift apart — there is only one source of
 * truth for capability category names.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface PluginCapability {

    org.juke.plugin.api.PluginCapability value();
}
