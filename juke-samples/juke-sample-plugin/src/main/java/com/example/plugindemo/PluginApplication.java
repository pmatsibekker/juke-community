package com.example.plugindemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Plugin SDK reference app. For a self-contained demo this one process plays
 * both roles:
 * <ul>
 *   <li>the <b>Juke agent</b> ({@code juke-remix-rest-service}, with the
 *       {@code /service/plugins/*} registration relay), and</li>
 *   <li>a <b>third-party plugin</b> built on {@code juke-plugin-sdk} —
 *       {@link DemoRecordingTransformer} declares a {@code RECORDING_TRANSFORMER}
 *       capability.</li>
 * </ul>
 *
 * <p>On {@code ApplicationReadyEvent} the SDK posts a registration to
 * {@code juke.plugin.remix-base-url} (pointed at this same process), so the
 * plugin then shows up in {@code GET /service/plugins}. In production the plugin
 * would be a separate service pointed at a remote agent.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"org.juke.framework", "org.juke.remix", "com.example.plugindemo"})
public class PluginApplication {

    public static void main(String[] args) {
        SpringApplication.run(PluginApplication.class, args);
    }
}
