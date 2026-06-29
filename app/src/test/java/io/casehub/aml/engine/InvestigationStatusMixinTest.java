package io.casehub.aml.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.aml.AmlJacksonConfig;
import io.casehub.aml.domain.InvestigationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvestigationStatusMixinTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        new AmlJacksonConfig().customize(mapper);
    }

    @Test
    void serializes_in_progress() throws Exception {
        assertEquals("\"in-progress\"", mapper.writeValueAsString(InvestigationStatus.IN_PROGRESS));
    }

    @Test
    void serializes_completed() throws Exception {
        assertEquals("\"completed\"", mapper.writeValueAsString(InvestigationStatus.COMPLETED));
    }

    @Test
    void deserializes_in_progress() throws Exception {
        assertEquals(InvestigationStatus.IN_PROGRESS,
                mapper.readValue("\"in-progress\"", InvestigationStatus.class));
    }

    @Test
    void deserializes_completed() throws Exception {
        assertEquals(InvestigationStatus.COMPLETED,
                mapper.readValue("\"completed\"", InvestigationStatus.class));
    }

    @Test
    void serializes_failed() throws Exception {
        assertEquals("\"failed\"", mapper.writeValueAsString(InvestigationStatus.FAILED));
    }

    @Test
    void serializes_cancelled() throws Exception {
        assertEquals("\"cancelled\"", mapper.writeValueAsString(InvestigationStatus.CANCELLED));
    }

    @Test
    void serializes_suspended() throws Exception {
        assertEquals("\"suspended\"", mapper.writeValueAsString(InvestigationStatus.SUSPENDED));
    }

    @Test
    void deserializes_failed() throws Exception {
        assertEquals(InvestigationStatus.FAILED,
                mapper.readValue("\"failed\"", InvestigationStatus.class));
    }

    @Test
    void deserializes_cancelled() throws Exception {
        assertEquals(InvestigationStatus.CANCELLED,
                mapper.readValue("\"cancelled\"", InvestigationStatus.class));
    }

    @Test
    void deserializes_suspended() throws Exception {
        assertEquals(InvestigationStatus.SUSPENDED,
                mapper.readValue("\"suspended\"", InvestigationStatus.class));
    }
}
