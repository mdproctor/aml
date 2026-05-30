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
        AmlInvestigationLedgerEntry caseOpened = entries.stream()
            .filter(e -> e instanceof AmlInvestigationLedgerEntry ale
                         && "CASE_OPENED".equals(ale.eventType))
            .map(e -> (AmlInvestigationLedgerEntry) e)
            .findFirst().orElseThrow(() -> new AssertionError("CASE_OPENED not found"));

        AmlInvestigationLedgerEntry reviewOpened = entries.stream()
            .filter(e -> e instanceof AmlInvestigationLedgerEntry ale
                         && "COMPLIANCE_REVIEW_OPENED".equals(ale.eventType))
            .map(e -> (AmlInvestigationLedgerEntry) e)
            .findFirst().orElseThrow(() -> new AssertionError("COMPLIANCE_REVIEW_OPENED not found"));

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
