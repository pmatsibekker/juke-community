package org.juke.remix.service;

import org.juke.framework.exception.JukeStorageException;
import org.juke.framework.storage.JukeStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 6 prereq — verify the agent's recording-pull endpoints round-trip
 * through {@link JukeStorage}'s recording-store API.
 */
class RemixRecordingsControllerTest {

    private JukeStorage storage;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        storage = mock(JukeStorage.class);
        mvc = MockMvcBuilders.standaloneSetup(new RemixRecordingsController(storage)).build();
    }

    @Test
    void listReturnsRecordingNamesAsJson() throws Exception {
        when(storage.listRecordings()).thenReturn(List.of("alpha", "beta", "gamma"));

        mvc.perform(get("/service/recordings"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0]").value("alpha"))
                .andExpect(jsonPath("$[2]").value("gamma"));
    }

    @Test
    void listReturnsEmptyArrayWhenNoRecordings() throws Exception {
        when(storage.listRecordings()).thenReturn(List.of());

        mvc.perform(get("/service/recordings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void loadReturnsZipBytesWithAttachmentDisposition() throws Exception {
        byte[] payload = new byte[]{0x50, 0x4b, 0x03, 0x04, 0x42, 0x42};   // "PK..." ZIP header bytes
        when(storage.loadRecording(eq("alpha"))).thenReturn(payload);

        mvc.perform(get("/service/recordings/alpha"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"alpha.zip\""))
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(content().bytes(payload));
    }

    @Test
    void loadReturns404WhenRecordingMissing() throws Exception {
        when(storage.loadRecording(eq("missing")))
                .thenThrow(new JukeStorageException("no such recording: missing"));

        mvc.perform(get("/service/recordings/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void loadHandlesNamesWithDots() throws Exception {
        // Recording names may carry a .zip suffix per the framework's
        // normalisation rules. Verify the path variable is decoded
        // correctly and forwarded verbatim.
        byte[] payload = "x".getBytes();
        when(storage.loadRecording(eq("track.with.dots"))).thenReturn(payload);

        mvc.perform(get("/service/recordings/track.with.dots"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(payload));
    }
}
