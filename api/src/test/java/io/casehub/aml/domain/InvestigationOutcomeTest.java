package io.casehub.aml.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InvestigationOutcomeTest {

    @Test
    void approved_maps_to_sar_filed() {
        final InvestigationOutcome outcome = InvestigationOutcome.fromReviewDecision("APPROVED");
        assertNotNull(outcome);
        assertEquals("sar-filed", outcome.type());
    }

    @Test
    void rejected_maps_to_gate_rejected() {
        final InvestigationOutcome outcome = InvestigationOutcome.fromReviewDecision("REJECTED");
        assertNotNull(outcome);
        assertEquals("gate-rejected", outcome.type());
    }

    @Test
    void unknown_maps_to_decision_not_recorded() {
        final InvestigationOutcome outcome = InvestigationOutcome.fromReviewDecision("UNKNOWN");
        assertNotNull(outcome);
        assertEquals("decision-not-recorded", outcome.type());
    }

    @Test
    void null_input_returns_null() {
        assertNull(InvestigationOutcome.fromReviewDecision(null));
    }

    @Test
    void unrecognised_value_throws() {
        assertThrows(IllegalStateException.class,
                () -> InvestigationOutcome.fromReviewDecision("SOMETHING_ELSE"));
    }
}
