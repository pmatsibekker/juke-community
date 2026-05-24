package org.juke.remix.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Forwards bare {@code /admin} and {@code /admin/} requests to
 * {@code /admin/index.html} so the React SPA loads on visit. Spring Boot's
 * default welcome-page handling only fires for the root context (/), not
 * for sub-paths like /admin/, so without this the only way to reach the
 * UI was to type the index.html filename explicitly.
 *
 * <p>The static resources themselves are served by Spring Boot's default
 * resource handler from {@code classpath:/static/admin/*} (the JAR layout
 * built by {@code juke-admin-ui}). React Router's {@code basename="/admin"}
 * takes over once {@code index.html} is loaded.
 */
@Configuration
public class AdminUiRoutingConfiguration implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/admin").setViewName("forward:/admin/index.html");
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
    }
}
