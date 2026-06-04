package io.casehub.aml.ledger;

import io.casehub.aml.AmlInvestigationApplicationService;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AmlLedgerCausedByTest {

    @Inject AmlInvestigationApplicationService service;
    @Inject LedgerEntryRepository ledgerRepo;

    @Test
    void complianceReviewEntry_hasCausedByEntryId_pointingToCaseOpened() {
        UUID caseId = service.investigate(tx("TXN-CB-001")).caseId();

        List<LedgerEntry> entries = ledgerRepo.findBySubjectId(caseId);
        AmlCaseOpenedLedgerEntry caseOpened = entries.stream()
            .filter(AmlCaseOpenedLedgerEntry.class::isInstance)
            .map(AmlCaseOpenedLedgerEntry.class::cast)
            .findFirst().orElseThrow(() -> new AssertionError("AmlCaseOpenedLedgerEntry not found"));

        AmlComplianceReviewLedgerEntry reviewOpened = entries.stream()
            .filter(AmlComplianceReviewLedgerEntry.class::isInstance)
            .map(AmlComplianceReviewLedgerEntry.class::cast)
            .findFirst().orElseThrow(() -> new AssertionError("AmlComplianceReviewLedgerEntry not found"));

        assertNotNull(reviewOpened.causedByEntryId,
            "COMPLIANCE_REVIEW_OPENED must have causedByEntryId set");
        assertEquals(caseOpened.id, reviewOpened.causedByEntryId,
            "causedByEntryId must point to the CASE_OPENED entry");
    }

    private SuspiciousTransaction tx(String id) {
        return new SuspiciousTransaction(id, "ACC-A", "ACC-B",
            new BigDecimal("50000"), "USD", Instant.now(), "Structuring");
    }
}
