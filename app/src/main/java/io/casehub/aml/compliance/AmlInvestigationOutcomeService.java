package io.casehub.aml.compliance;

import io.casehub.aml.domain.InvestigationOutcome;
import io.casehub.aml.ledger.AmlSarOfficerReviewedLedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.UUID;

@ApplicationScoped
public class AmlInvestigationOutcomeService {

    private static final Comparator<AmlSarOfficerReviewedLedgerEntry> HUMAN_FIRST_LATEST_SEQ =
            Comparator.<AmlSarOfficerReviewedLedgerEntry, Integer>comparing(
                    e -> e.actorType == ActorType.HUMAN ? 0 : 1)
            .thenComparing(e -> e.sequenceNumber, Comparator.reverseOrder());

    private final LedgerEntryRepository ledgerEntryRepository;

    @Inject
    public AmlInvestigationOutcomeService(final LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    public InvestigationOutcome resolve(final UUID caseId) {
        return ledgerEntryRepository
                .findBySubjectId(caseId, TenancyConstants.DEFAULT_TENANT_ID).stream()
                .filter(AmlSarOfficerReviewedLedgerEntry.class::isInstance)
                .map(AmlSarOfficerReviewedLedgerEntry.class::cast)
                .min(HUMAN_FIRST_LATEST_SEQ)
                .map(e -> InvestigationOutcome.fromReviewDecision(e.reviewDecision))
                .orElse(null);
    }
}
