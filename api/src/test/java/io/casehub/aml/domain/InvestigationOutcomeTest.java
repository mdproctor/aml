package io.casehub.aml.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InvestigationOutcomeTest {

    @Test
    void approved_maps_to_sar_filed() {
        final InvestigationOutcome outcome = InvestigationOutcome.fromReviewDecision("APPROVED", null);
        assertNotNull(outcome);
        assertEquals("sar-filed", outcome.type());
        assertNull(outcome.reason());
    }

    @Test
    void rejected_maps_to_gate_rejected() {
        final InvestigationOutcome outcome = InvestigationOutcome.fromReviewDecision("REJECTED", "Insufficient evidence");
        assertNotNull(outcome);
        assertEquals("gate-rejected", outcome.type());
        assertEquals("Insufficient evidence", outcome.reason());
    }

    @Test
    void rejected_without_reason() {
        final InvestigationOutcome outcome = InvestigationOutcome.fromReviewDecision("REJECTED", null);
        assertNotNull(outcome);
        assertEquals("gate-rejected", outcome.type());
        assertNull(outcome.reason());
    }

    @Test
    void unknown_maps_to_decision_not_recorded() {
        final InvestigationOutcome outcome = InvestigationOutcome.fromReviewDecision("UNKNOWN", null);
        assertNotNull(outcome);
        assertEquals("decision-not-recorded", outcome.type());
        assertNull(outcome.reason());
    }

    @Test
    void null_reviewDecision_throws() {
        assertThrows(NullPointerException.class,
                () -> InvestigationOutcome.fromReviewDecision(null, null));
    }

    @Test
    void unrecognised_value_throws() {
        assertThrows(IllegalStateException.class,
                () -> InvestigationOutcome.fromReviewDecision("SOMETHING_ELSE", null));
    }
}
