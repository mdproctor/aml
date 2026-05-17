package io.casehub.aml.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpecialistOutcomeTest {

    @Test
    void completedHoldsResult() {
        var outcome = new SpecialistOutcome.Completed<>("found");
        assertEquals("found", outcome.result());
    }

    @Test
    void declinedHoldsAgentInfo() {
        var d = new SpecialistOutcome.Declined<String>("agent-1", "osint-screening", "insufficient clearance");
        assertEquals("agent-1", d.agentId());
        assertEquals("osint-screening", d.capability());
        assertEquals("insufficient clearance", d.reason());
    }

    @Test
    void failedHoldsAgentInfo() {
        var f = new SpecialistOutcome.Failed<String>("agent-2", "entity-resolution", "timeout");
        assertEquals("agent-2", f.agentId());
        assertEquals("entity-resolution", f.capability());
        assertEquals("timeout", f.reason());
    }

    @Test
    void patternMatchIsExhaustive() {
        SpecialistOutcome<String> outcome = new SpecialistOutcome.Declined<>("a", "cap", "no access");
        String tag = switch (outcome) {
            case SpecialistOutcome.Completed<String> c -> "completed";
            case SpecialistOutcome.Declined<String> d  -> "declined:" + d.reason();
            case SpecialistOutcome.Failed<String> f    -> "failed";
        };
        assertEquals("declined:no access", tag);
    }
}
