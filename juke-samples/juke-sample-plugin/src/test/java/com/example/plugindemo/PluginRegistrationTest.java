package com.example.plugindemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Proves the plugin SDK registration relay end to end: a {@code RECORDING_TRANSFORMER}
 * capability built on {@code juke-plugin-sdk} auto-registers with the co-located
 * agent on startup and is then listed by {@code GET /service/plugins}. Headless,
 * runs under {@code mvn test}.
 *
 * <p>Registration fires on {@code ApplicationReadyEvent}, so we poll the agent's
 * plugin list until the plugin appears (or time out).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "server.port=18097",
                "juke.enabled=true",
                "juke.plugin.enabled=true",
                "juke.plugin.plugin-id=demo-transformer",
                "juke.plugin.base-url=http://localhost:18097",
                "juke.plugin.remix-base-url=http://localhost:18097"
        })
class PluginRegistrationTest {

    @Autowired
    TestRestTemplate http;

    @Test
    void pluginSelfRegisters_andIsListedWithItsCapability() throws InterruptedException {
        String body = null;
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            body = http.getForObject("/service/plugins", String.class);
            if (body != null && body.contains("demo-transformer")) {
                break;
            }
            Thread.sleep(500);
        }
        if (body == null || !body.contains("demo-transformer")) {
            fail("plugin did not register within timeout; /service/plugins = " + body);
        }
        assertTrue(body.contains("RECORDING_TRANSFORMER"),
                "the registered plugin should advertise its RECORDING_TRANSFORMER capability; was: " + body);
    }
}
