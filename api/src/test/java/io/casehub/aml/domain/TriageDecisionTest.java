package io.casehub.aml.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TriageDecisionTest {

    @Test
    void valueOf_roundTrips() {
        assertEquals("SAR_WARRANTED", TriageDecision.valueOf("SAR_WARRANTED").name());
        assertEquals("FALSE_POSITIVE", TriageDecision.valueOf("FALSE_POSITIVE").name());
        assertEquals("INCONCLUSIVE", TriageDecision.valueOf("INCONCLUSIVE").name());
    }

    @Test
    void values_hasExactlyThree() {
        assertEquals(3, TriageDecision.values().length);
    }

    @Test
    void valueOf_unknownThrows() {
        assertThrows(IllegalArgumentException.class,
                     () -> TriageDecision.valueOf("SAR_FILED"));
    }
}
