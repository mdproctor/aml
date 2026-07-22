package io.casehub.aml.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsintResultTest {

    @Test
    void completedScreening_sanctionsHit() {
        var result = new OsintResult(true, false, false, "OFAC/SDN match");
        assertTrue(result.sanctionsHit());
        assertFalse(result.declined());
    }

    @Test
    void completedScreening_pepHit() {
        var result = new OsintResult(false, true, false, "PEP database match");
        assertTrue(result.pepHit());
    }

    @Test
    void completedScreening_clean() {
        var result = new OsintResult(false, false, false, "no matches");
        assertFalse(result.sanctionsHit());
        assertFalse(result.pepHit());
        assertFalse(result.declined());
    }

    @Test
    void declinedScreening_valid() {
        var result = new OsintResult(false, false, true, "insufficient clearance");
        assertTrue(result.declined());
        assertEquals("insufficient clearance", result.reason());
    }

    @Test
    void declined_withSanctionsHit_throws() {
        var ex = assertThrows(IllegalArgumentException.class,
                              () -> new OsintResult(true, false, true, "declined"));
        assertTrue(ex.getMessage().contains("declined screening cannot report sanctions or PEP hits"));
    }

    @Test
    void declined_withPepHit_throws() {
        var ex = assertThrows(IllegalArgumentException.class,
                              () -> new OsintResult(false, true, true, "declined"));
        assertTrue(ex.getMessage().contains("declined screening cannot report sanctions or PEP hits"));
    }
}
