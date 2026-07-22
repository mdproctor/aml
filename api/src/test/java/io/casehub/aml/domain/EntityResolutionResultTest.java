package io.casehub.aml.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityResolutionResultTest {

    @Test
    void validRiskScore() {
        var result = new EntityResolutionResult("E-1", "chain", "CORPORATE", 0.5);
        assertEquals(0.5, result.riskScore());
    }

    @Test
    void riskScoreAtBounds() {
        assertEquals(0.0, new EntityResolutionResult("E-1", "c", "PEP", 0.0).riskScore());
        assertEquals(1.0, new EntityResolutionResult("E-1", "c", "PEP", 1.0).riskScore());
    }

    @Test
    void riskScoreBelowZero_throws() {
        var ex = assertThrows(IllegalArgumentException.class,
                              () -> new EntityResolutionResult("E-1", "c", "PEP", -0.1));
        assertTrue(ex.getMessage().contains("riskScore must be in [0.0, 1.0]"));
    }

    @Test
    void riskScoreAboveOne_throws() {
        var ex = assertThrows(IllegalArgumentException.class,
                              () -> new EntityResolutionResult("E-1", "c", "PEP", 1.1));
        assertTrue(ex.getMessage().contains("riskScore must be in [0.0, 1.0]"));
    }
}
