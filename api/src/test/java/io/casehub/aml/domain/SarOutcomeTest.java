package io.casehub.aml.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SarOutcomeTest {

    @Test
    void upheld_verdict_accepted() {
        final SarOutcome o = new SarOutcome(SarVerdict.UPHELD, "SAR upheld by FinCEN", 0.92);
        assertEquals(SarVerdict.UPHELD, o.verdict());
        assertEquals("SAR upheld by FinCEN", o.reason());
        assertEquals(0.92, o.investigationAccuracyScore(), 0.001);
    }

    @Test
    void flagged_verdict_accepted() {
        final SarOutcome o = new SarOutcome(SarVerdict.FLAGGED, "Incomplete evidence chain", 0.30);
        assertEquals(SarVerdict.FLAGGED, o.verdict());
        assertEquals(0.30, o.investigationAccuracyScore(), 0.001);
    }

    @Test
    void all_verdicts_are_defined() {
        assertEquals(3, SarVerdict.values().length);
        assertNotNull(SarVerdict.valueOf("UPHELD"));
        assertNotNull(SarVerdict.valueOf("WITHDRAWN"));
        assertNotNull(SarVerdict.valueOf("FLAGGED"));
    }
}
