package io.casehub.aml.tutorial;

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

class NaiveSarDraftingServiceTest {

    private final NaiveSarDraftingService service = new NaiveSarDraftingService();

    private final SuspiciousTransaction tx = new SuspiciousTransaction(
            "TXN-SAR", "ACC-A", "ACC-B",
            new BigDecimal("50000"), "USD",
            Instant.parse("2024-01-01T00:00:00Z"), "Test");

    private final SpecialistOutcome<EntityResolutionResult> completedEntity =
            new SpecialistOutcome.Completed<>(new EntityResolutionResult("E-1", "A -> B"));
    private final SpecialistOutcome<PatternAnalysisResult> completedPattern =
            new SpecialistOutcome.Completed<>(new PatternAnalysisResult(true, "structuring"));

    @Test
    void draft_withCompletedOsint_includesTransactionId() {
        SpecialistOutcome<OsintResult> osint = new SpecialistOutcome.Completed<>(new OsintResult(false, false, "clean"));
        String narrative = service.draft(tx, completedEntity, completedPattern, osint);
        assertNotNull(narrative);
        assertTrue(narrative.contains("TXN-SAR"), "Narrative should reference transaction ID");
    }

    @Test
    void draft_withDeclinedOsint_includesDeclineInNarrative() {
        SpecialistOutcome<OsintResult> osint = new SpecialistOutcome.Declined<>(
                "osint-agent", "osint-screening", "insufficient clearance for PEP database access");
        String narrative = service.draft(tx, completedEntity, completedPattern, osint);
        assertNotNull(narrative);
        assertTrue(narrative.contains("declined") || narrative.contains("clearance"),
                "Narrative should reference the OSINT decline: " + narrative);
    }

    @Test
    void draft_withFailedOsint_includesFailureInNarrative() {
        SpecialistOutcome<OsintResult> osint = new SpecialistOutcome.Failed<>(
                "osint-agent", "osint-screening", "connection timeout");
        String narrative = service.draft(tx, completedEntity, completedPattern, osint);
        assertNotNull(narrative);
        assertTrue(narrative.contains("failed") || narrative.contains("manual"),
                "Narrative should reference the OSINT failure: " + narrative);
    }
}
