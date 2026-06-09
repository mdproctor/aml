package io.casehub.aml.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.aml.AmlInvestigationApplicationService;
import io.casehub.aml.domain.AmlInvestigationResult;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.quarkus.test.junit.QuarkusTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layer 4/8: verifies that AML domain-level ledger entries (AmlCaseOpenedLedgerEntry,
 * AmlComplianceReviewLedgerEntry) are written for each investigation, and that
 * the HTTP-visible result includes a ledger entry reference for independent verification.
 *
 * <p>Note: {@code @TestTransaction} is intentionally omitted. Rolling back after each test
 * would prevent {@code ledgerRepo.findBySubjectId()} from seeing the entries written during
 * the investigation (they would be in the rolled-back outer transaction). Tests use unique
 * transaction IDs to avoid cross-test interference.
 */
@QuarkusTest
class AmlLedgerChainTest {

    @Inject
    AmlInvestigationApplicationService service;

    @Inject
    LedgerEntryRepository ledgerRepo;

    @Test
    void investigate_returnsNonNullLedgerCaseEntryId() {
        AmlInvestigationResult result = service.investigate(sampleTx("TXN-L4-001"));
        assertNotNull(result.ledgerCaseEntryId(), "ledgerCaseEntryId must be set");
    }

    @Test
    void investigate_returnsCaseId() {
        AmlInvestigationResult result = service.investigate(sampleTx("TXN-L4-002"));
        assertNotNull(result.caseId(), "caseId must be set");
    }

    @Test
    void investigate_caseOpenedEntryExistsInLedger() {
        AmlInvestigationResult result = service.investigate(sampleTx("TXN-L4-003"));
        var entry = ledgerRepo.findEntryById(result.ledgerCaseEntryId(), io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID);
        assertTrue(entry.isPresent(), "CASE_OPENED entry must be findable by its UUID");
        assertEquals(result.caseId(), entry.get().subjectId,
                "entry subjectId must equal the case UUID");
    }

    @Test
    void investigate_caseOpenedEntry_isCorrectType() {
        AmlInvestigationResult result = service.investigate(sampleTx("TXN-L4-004"));
        var entry = ledgerRepo.findEntryById(result.ledgerCaseEntryId(), io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID)
                .orElseThrow(() -> new AssertionError("CASE_OPENED entry not found"));
        assertTrue(entry instanceof AmlCaseOpenedLedgerEntry,
                "CASE_OPENED entry must be AmlCaseOpenedLedgerEntry");
    }

    @Test
    void investigate_atLeastTwoAmlEntriesExistPerCase() {
        UUID caseId = service.investigate(sampleTx("TXN-L4-005")).caseId();
        List<LedgerEntry> entries = ledgerRepo.findBySubjectId(caseId, io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID);
        assertTrue(entries.size() >= 2,
                "Expected at least CASE_OPENED and COMPLIANCE_REVIEW_OPENED, got " + entries.size());
    }

    @Test
    void investigate_complianceReviewEntry_isCorrectType() {
        UUID caseId = service.investigate(sampleTx("TXN-L4-006")).caseId();
        List<LedgerEntry> entries = ledgerRepo.findBySubjectId(caseId, io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID);
        boolean hasReviewEntry = entries.stream()
                .anyMatch(AmlComplianceReviewLedgerEntry.class::isInstance);
        assertTrue(hasReviewEntry, "AmlComplianceReviewLedgerEntry must be written after review is opened");
    }

    private SuspiciousTransaction sampleTx(final String id) {
        return new SuspiciousTransaction(
                id, "ACC-A", "ACC-B",
                new BigDecimal("75000"), "USD",
                Instant.now(), "Structuring");
    }
}
