package io.casehub.aml.agents;

import org.junit.jupiter.api.Test;

import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.SpecialistOutcome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class OsintScreeningBehaviourTest {

    @Test
    void handle_alwaysReturnsDeclined() {
        OsintScreeningBehaviour behaviour = new OsintScreeningBehaviour();
        SpecialistOutcome<?> outcome = behaviour.handle(null);

        assertInstanceOf(SpecialistOutcome.Declined.class, outcome);
    }

    @Test
    void handle_declineContainsPepReason() {
        OsintScreeningBehaviour behaviour = new OsintScreeningBehaviour();
        SpecialistOutcome.Declined<?> declined = (SpecialistOutcome.Declined<?>) behaviour.handle(null);

        assertEquals("osint-screening", declined.capability());
        assertInstanceOf(String.class, declined.reason());
        org.junit.jupiter.api.Assertions.assertTrue(
                declined.reason().toLowerCase().contains("clearance") ||
                declined.reason().toLowerCase().contains("pep"),
                "Decline reason should mention clearance or PEP: " + declined.reason());
    }
}
