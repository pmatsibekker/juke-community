package com.example.session;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke test: the Spring context boots and the @Juke("none")-wrapped
 * controller has its service field wired. Cookie-isolation behaviour
 * is exercised by the Playwright specs in src/test/playwright/.
 */
@SpringBootTest(properties = {
        "juke.enabled=true",
        "juke.path=${java.io.tmpdir}",
        "juke.zip=session-track-test"
})
class SessionApplicationTest {

    @Autowired
    private SessionGreetingController controller;

    @Test
    void contextLoads() {
        assertNotNull(controller, "SessionGreetingController should be wired");
    }
}
