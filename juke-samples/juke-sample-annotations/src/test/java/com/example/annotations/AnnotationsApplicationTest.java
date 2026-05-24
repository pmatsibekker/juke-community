package com.example.annotations;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke test: the context boots, both example components are wired,
 * and {@code JukeBeanPostProcessor} has replaced the {@code @Juke}
 * fields with proxies (verified implicitly — if the field types
 * didn't match the proxies, the autowire would fail at startup).
 */
@SpringBootTest(properties = {
        "juke.enabled=true",
        "juke.path=${java.io.tmpdir}",
        "juke.zip=annotations-reference-test"
})
class AnnotationsApplicationTest {

    @Autowired
    private JukeAnnotationExample annotationExample;

    @Autowired
    private JukeConstructorExample constructorExample;

    @Test
    void contextLoads() {
        assertNotNull(annotationExample);
        assertNotNull(constructorExample);
    }
}
