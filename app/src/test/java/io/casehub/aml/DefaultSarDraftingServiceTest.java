package io.casehub.aml;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SpecialistOutcome;
import io.casehub.aml.domain.SuspiciousTransaction;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSarDraftingServiceTest {

    private final DefaultSarDraftingService service = new DefaultSarDraftingService();

    private final SuspiciousTransaction tx = new SuspiciousTransaction(
            "TXN-SAR", "ACC-A", "ACC-B",
            new BigDecimal("50000"), "USD",
            Instant.parse("2024-01-01T00:00:00Z"), "Test");

    private final SpecialistOutcome<EntityResolutionResult> completedEntity =
            new SpecialistOutcome.Completed<>(new EntityResolutionResult("E-1", "A -> B", "CORPORATE", 0.35));
    private final SpecialistOutcome<PatternAnalysisResult> completedPattern =
            new SpecialistOutcome.Completed<>(new PatternAnalysisResult(true, "structuring"));
    private final SpecialistOutcome<OsintResult> completedOsint =
            new SpecialistOutcome.Completed<>(new OsintResult(false, false, "clean"));

    private final SpecialistOutcome<EntityResolutionResult> declinedEntity =
            new SpecialistOutcome.Declined<>("entity-agent", "entity-resolution", "insufficient clearance");
    private final SpecialistOutcome<EntityResolutionResult> failedEntity =
            new SpecialistOutcome.Failed<>("entity-agent", "entity-resolution", "timeout");

    private final SpecialistOutcome<PatternAnalysisResult> declinedPattern =
            new SpecialistOutcome.Declined<>("pattern-agent", "pattern-analysis", "insufficient data");
    private final SpecialistOutcome<PatternAnalysisResult> failedPattern =
            new SpecialistOutcome.Failed<>("pattern-agent", "pattern-analysis", "connection timeout");

    private final SpecialistOutcome<OsintResult> declinedOsint =
            new SpecialistOutcome.Declined<>("osint-agent", "osint-screening", "insufficient clearance for PEP database access");
    private final SpecialistOutcome<OsintResult> failedOsint =
            new SpecialistOutcome.Failed<>("osint-agent", "osint-screening", "connection timeout");

    @Test
    void draft_withCompletedOsint_includesTransactionId() {
        String narrative = service.draft(tx, completedEntity, completedPattern, completedOsint);
        assertNotNull(narrative);
        assertTrue(narrative.contains("TXN-SAR"), "Narrative should reference transaction ID");
    }

    @Test
    void draft_withDeclinedOsint_includesDeclineInNarrative() {
        String narrative = service.draft(tx, completedEntity, completedPattern, declinedOsint);
        assertNotNull(narrative);
        assertTrue(narrative.contains("declined") || narrative.contains("clearance"),
                "Narrative should reference the OSINT decline: " + narrative);
    }

    @Test
    void draft_withFailedOsint_includesFailureInNarrative() {
        String narrative = service.draft(tx, completedEntity, completedPattern, failedOsint);
        assertNotNull(narrative);
        assertTrue(narrative.contains("failed") || narrative.contains("manual"),
                "Narrative should reference the OSINT failure: " + narrative);
    }

    @Test
    void draft_withDeclinedEntity_includesDeclineInNarrative() {
        String narrative = service.draft(tx, declinedEntity, completedPattern, completedOsint);
        assertNotNull(narrative);
        assertTrue(narrative.contains("declined") || narrative.contains("clearance"),
                "Narrative should reference the entity decline: " + narrative);
    }

    @Test
    void draft_withFailedEntity_includesFailureInNarrative() {
        String narrative = service.draft(tx, failedEntity, completedPattern, completedOsint);
        assertNotNull(narrative);
        assertTrue(narrative.contains("failed") || narrative.contains("timeout"),
                "Narrative should reference the entity failure: " + narrative);
    }

    @Test
    void draft_withDeclinedPattern_includesDeclineInNarrative() {
        String narrative = service.draft(tx, completedEntity, declinedPattern, completedOsint);
        assertNotNull(narrative);
        assertTrue(narrative.contains("declined") || narrative.contains("data"),
                "Narrative should reference the pattern decline: " + narrative);
    }

    @Test
    void draft_withFailedPattern_includesFailureInNarrative() {
        String narrative = service.draft(tx, completedEntity, failedPattern, completedOsint);
        assertNotNull(narrative);
        assertTrue(narrative.contains("failed") || narrative.contains("timeout"),
                "Narrative should reference the pattern failure: " + narrative);
    }
}
