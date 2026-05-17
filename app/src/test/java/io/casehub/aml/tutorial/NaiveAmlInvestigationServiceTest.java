package io.casehub.aml.tutorial;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.casehub.aml.domain.InvestigationSummary;
import io.casehub.aml.domain.SpecialistOutcome;
import io.casehub.aml.domain.SuspiciousTransaction;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class NaiveAmlInvestigationServiceTest {

    private final NaiveAmlInvestigationService service = new NaiveAmlInvestigationService();

    private SuspiciousTransaction tx(String id) {
        return new SuspiciousTransaction(
                id, "ACC-A", "ACC-B",
                new BigDecimal("100000"), "USD",
                Instant.parse("2024-03-15T10:00:00Z"), "Structuring");
    }

    @Test
    void investigate_validTransaction_returnsCompleteSummary() {
        InvestigationSummary summary = service.investigate(tx("TXN-001"));

        assertNotNull(summary);
        assertNotNull(summary.entityResolution());
        assertNotNull(summary.patternAnalysis());
        assertNotNull(summary.osintScreening());
        assertNotNull(summary.sarNarrative());
    }

    @Test
    void investigate_allOutcomesAreCompleted() {
        InvestigationSummary summary = service.investigate(tx("TXN-002"));

        assertInstanceOf(SpecialistOutcome.Completed.class, summary.entityResolution());
        assertInstanceOf(SpecialistOutcome.Completed.class, summary.patternAnalysis());
        assertInstanceOf(SpecialistOutcome.Completed.class, summary.osintScreening());
    }

    @Test
    void investigate_preservesTransactionIdentity() {
        SuspiciousTransaction input = tx("TXN-003");
        assertSame(input, service.investigate(input).transaction());
    }

    @Test
    void investigate_calledTwice_producesIndependentResults() {
        InvestigationSummary first  = service.investigate(tx("TXN-004"));
        InvestigationSummary second = service.investigate(tx("TXN-005"));

        assertNotNull(first);
        assertNotNull(second);
        assertNotSame(first, second);
    }
}
