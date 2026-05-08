package io.casehub.aml.tutorial;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.casehub.aml.domain.InvestigationSummary;
import io.casehub.aml.domain.SuspiciousTransaction;

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

    // Happy path: a valid transaction produces a fully populated summary
    @Test
    void investigate_validTransaction_returnsCompleteSummary() {
        InvestigationSummary summary = service.investigate(tx("TXN-001"));

        assertNotNull(summary);
        assertNotNull(summary.entityResolution());
        assertNotNull(summary.patternAnalysis());
        assertNotNull(summary.osintScreening());
        assertNotNull(summary.sarNarrative());
    }

    // Correctness: the original transaction object is preserved unchanged in the summary
    @Test
    void investigate_preservesTransactionIdentity() {
        SuspiciousTransaction input = tx("TXN-002");
        assertSame(input, service.investigate(input).transaction());
    }

    // Correctness: two successive calls produce independent summary objects
    @Test
    void investigate_calledTwice_producesIndependentSummaries() {
        InvestigationSummary first  = service.investigate(tx("TXN-003"));
        InvestigationSummary second = service.investigate(tx("TXN-004"));

        assertNotNull(first);
        assertNotNull(second);
        assertNotSame(first, second);
    }
}
