package io.casehub.aml.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlagReasonTest {

    @Test
    void allValues_roundTrip() {
        for (FlagReason reason : FlagReason.values()) {
            assertEquals(reason, FlagReason.valueOf(reason.name()));
        }
    }

    @Test
    void expectedValues_present() {
        assertNotNull(FlagReason.valueOf("STRUCTURING"));
        assertNotNull(FlagReason.valueOf("LAYERING"));
        assertNotNull(FlagReason.valueOf("SMURFING"));
        assertNotNull(FlagReason.valueOf("ROUND_TRIP"));
        assertNotNull(FlagReason.valueOf("PEP_MATCH"));
        assertNotNull(FlagReason.valueOf("HIGH_RISK_JURISDICTION"));
        assertNotNull(FlagReason.valueOf("VELOCITY_ANOMALY"));
        assertNotNull(FlagReason.valueOf("LARGE_VOLUME"));
    }

    @Test
    void count_isEight() {
        assertEquals(8, FlagReason.values().length);
    }
}
