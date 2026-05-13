package io.casehub.aml.tutorial;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.casehub.aml.domain.AmlInvestigationResult;
import io.casehub.aml.domain.SuspiciousTransaction;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        AmlInvestigationResult result = service.investigate(tx("TXN-001"));

        assertNotNull(result);
        assertNotNull(result.summary());
        assertNotNull(result.summary().entityResolution());
        assertNotNull(result.summary().patternAnalysis());
        assertNotNull(result.summary().osintScreening());
        assertNotNull(result.summary().sarNarrative());
    }

    @Test
    void investigate_noWorkItem_complianceReviewTaskIdIsNull() {
        AmlInvestigationResult result = service.investigate(tx("TXN-001"));
        assertNull(result.complianceReviewTaskId());
    }

    @Test
    void investigate_preservesTransactionIdentity() {
        SuspiciousTransaction input = tx("TXN-002");
        assertSame(input, service.investigate(input).summary().transaction());
    }

    @Test
    void investigate_calledTwice_producesIndependentResults() {
        AmlInvestigationResult first  = service.investigate(tx("TXN-003"));
        AmlInvestigationResult second = service.investigate(tx("TXN-004"));

        assertNotNull(first);
        assertNotNull(second);
        assertNotSame(first, second);
    }
}
