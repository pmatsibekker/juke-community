package com.example.todo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke test: the Spring context boots and the @Juke-wrapped
 * {@link ToDoDAO} is wired. Examples don't carry a coverage SLA.
 */
@SpringBootTest(properties = {
        "juke.enabled=true",
        "juke.path=${java.io.tmpdir}",
        "juke.zip=todo-track-test"
})
class TodoApplicationTest {

    @Autowired
    private ToDoDAO dao;

    @Test
    void contextLoads() {
        assertNotNull(dao, "ToDoDAO should be wired");
    }
}
