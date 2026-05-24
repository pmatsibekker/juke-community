package com.example.greeting;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke test: the Spring context boots and the Juke-wrapped greeting
 * DAO is available. Examples don't carry a coverage SLA — this is
 * deliberately a minimal "does it stand up?" check.
 */
@SpringBootTest(properties = {
        "juke.enabled=true",
        "juke.path=${java.io.tmpdir}",
        "juke.zip=greeting-track-test"
})
class GreetingApplicationTest {

    @Autowired
    private JukeGreetingsDAO dao;

    @Test
    void contextLoads() {
        assertNotNull(dao, "JukeGreetingsDAO should be wired");
    }
}
