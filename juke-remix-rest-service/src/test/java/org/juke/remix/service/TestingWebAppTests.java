package org.juke.remix.service;

import com.example.Greeting;
import com.example.GreetingController;
import com.example.GreetingServiceImpl;
import com.example.IGreetingController;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.juke.framework.storage.ZipUtil;
import org.juke.framework.exception.JukeAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(properties={"juke.enabled=true","juke.path=TMP_PLACEHOLDER","juke.zip=juketest2","juke.tests=juketest,juketest2","juke=replay"})
@TestPropertySource(properties={"juke.enabled=true","juke.path=TMP_PLACEHOLDER","juke.zip=juketest2","juke.tests=juketest,juketest2","juke=replay"})
@ActiveProfiles("replay")
@ContextConfiguration(classes = {RecordingServiceImpl.class, ReplayServiceImpl.class, TunerServiceImpl.class,
        GreetingController.class, GreetingServiceImpl.class})
class TestingWebAppTests {
    @Autowired
    RecordingService recordingService;
    @Autowired
    ReplayService replayService;
    @Autowired
    TunerService tunerService;
    @Autowired
    IGreetingController greetingController;
    static boolean isLoaded=false;
    private static Logger log = LoggerFactory.getLogger(TestingWebAppTests.class);

    static Path tempJukeZip;

    static {
        try {
            // Copy the legacy zip from test resources to the temp directory
            String zipName = "juketest2.zip";
            InputStream in = TestingWebAppTests.class.getClassLoader().getResourceAsStream(zipName);
            if (in == null) throw new IOException(zipName + " not found in test resources");
            tempJukeZip = Path.of(System.getProperty("java.io.tmpdir"), zipName);
            Files.copy(in, tempJukeZip, StandardCopyOption.REPLACE_EXISTING);
            in.close();
            // Set system properties to use the temp directory and zip (without .zip extension)
            System.setProperty("juke.path", System.getProperty("java.io.tmpdir"));
            System.setProperty("juke.zip", "juketest2");
            System.setProperty("juke.tests", "juketest,juketest2");
            System.setProperty("juke", "replay");
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy juketest2.zip to temp directory", e);
        }
    }

    @BeforeAll
    void buildTestFile() {
        // No-op: static block handles resource copy
    }

    @Test
    void contextLoads() {
        assert(recordingService != null);
        assert(replayService != null);
        assert(tunerService != null);
        assert(greetingController != null);
    }

    @Test
    void testGreeting() {
        log.debug("isLoaded: " + isLoaded);

        Greeting greeting  = greetingController.greeting("World");
        assertEquals("Hello, test!", greeting.getContent());
        greeting = greetingController.greeting("World");
        assertEquals("Hello, World!", greeting.getContent());
        greeting = greetingController.greeting("World");
        assertEquals("Hello, cruel world!", greeting.getContent());
    }
}
