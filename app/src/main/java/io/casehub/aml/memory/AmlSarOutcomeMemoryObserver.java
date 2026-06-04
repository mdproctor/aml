package io.casehub.aml.memory;

import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.engine.SarOutcomeRecordedEvent;
import io.casehub.aml.ledger.AmlCaseOpenedLedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;

@ApplicationScoped
public class AmlSarOutcomeMemoryObserver {

    private static final Logger LOG = Logger.getLogger(AmlSarOutcomeMemoryObserver.class);

    @Inject LedgerEntryRepository ledgerRepository;
    @Inject AmlMemoryService memoryService;

    @Transactional(TxType.REQUIRES_NEW)
    public void onSarOutcome(@Observes final SarOutcomeRecordedEvent event) {
        // REQUIRES_NEW starts a new JTA transaction governing the default datasource (memory-jpa).
        // The ledger read below uses LedgerEntryRepository's @PersistenceContext(unitName="qhorus"),
        // which is a separate EntityManager and not bound by this transaction. The AmlCaseOpenedLedgerEntry
        // was written during startInvestigation() in a prior request that has already committed, so
        // reading it here is safe regardless of the outer transaction boundary.
        final AmlCaseOpenedLedgerEntry caseEntry = ledgerRepository
                .findBySubjectId(event.caseId()).stream()
                .filter(AmlCaseOpenedLedgerEntry.class::isInstance)
                .map(AmlCaseOpenedLedgerEntry.class::cast)
                .findFirst()
                .orElse(null);

        if (caseEntry == null) {
            LOG.warnf("No AmlCaseOpenedLedgerEntry found for caseId=%s — skipping SAR memory write",
                    event.caseId());
            return;
        }

        final SuspiciousTransaction transaction = new SuspiciousTransaction(
            caseEntry.transactionId,
            caseEntry.originAccountId,
            caseEntry.destinationAccountId,
            BigDecimal.ZERO,
            "UNKNOWN",
            Instant.EPOCH,
            "SAR_OUTCOME");
        memoryService.storeSarOutcome(event.caseId(), transaction, event.outcome());
        LOG.infof("SAR outcome memory stored: caseId=%s verdict=%s",
                event.caseId(), event.outcome().verdict());
    }
}
