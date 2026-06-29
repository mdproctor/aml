package io.casehub.aml.engine;

import io.casehub.aml.domain.InvestigationOutcome;
import io.casehub.aml.domain.InvestigationResolution;
import io.casehub.aml.domain.InvestigationStatus;
import io.casehub.aml.ledger.AmlSarOfficerReviewedLedgerEntry;
import io.casehub.api.model.CaseStatus;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.CaseInstanceRepository;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AmlInvestigationOutcomeService {

    private static final Comparator<AmlSarOfficerReviewedLedgerEntry> HUMAN_FIRST_LATEST_SEQ =
            Comparator.<AmlSarOfficerReviewedLedgerEntry, Integer>comparing(
                    e -> e.actorType == ActorType.HUMAN ? 0 : 1)
            .thenComparing(e -> e.sequenceNumber, Comparator.reverseOrder());

    private final LedgerEntryRepository ledgerEntryRepository;
    private final CaseInstanceCache caseInstanceCache;
    private final CaseInstanceRepository caseInstanceRepository;

    @Inject
    public AmlInvestigationOutcomeService(
            final LedgerEntryRepository ledgerEntryRepository,
            final CaseInstanceCache caseInstanceCache,
            final CaseInstanceRepository caseInstanceRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.caseInstanceCache = caseInstanceCache;
        this.caseInstanceRepository = caseInstanceRepository;
    }

    public Optional<InvestigationResolution> resolveInvestigation(final UUID caseId) {
        CaseInstance instance = caseInstanceCache.get(caseId);
        if (instance == null) {
            instance = caseInstanceRepository
                    .findByUuid(caseId, TenancyConstants.DEFAULT_TENANT_ID)
                    .await().indefinitely();
        }
        if (instance == null) {
            return Optional.empty();
        }

        InvestigationStatus status = switch (instance.getState()) {
            case STARTING, RUNNING, WAITING -> InvestigationStatus.IN_PROGRESS;
            case COMPLETED -> InvestigationStatus.COMPLETED;
            case FAULTED -> InvestigationStatus.FAILED;
            case CANCELLED -> InvestigationStatus.CANCELLED;
            case SUSPENDED -> InvestigationStatus.SUSPENDED;
        };

        if (status != InvestigationStatus.COMPLETED) {
            return Optional.of(new InvestigationResolution(status, null));
        }
        final InvestigationOutcome outcome = resolveOutcome(caseId);
        return Optional.of(new InvestigationResolution(InvestigationStatus.COMPLETED, outcome));
    }

    InvestigationOutcome resolveOutcome(final UUID caseId) {
        return ledgerEntryRepository
                .findBySubjectId(caseId, TenancyConstants.DEFAULT_TENANT_ID).stream()
                .filter(AmlSarOfficerReviewedLedgerEntry.class::isInstance)
                .map(AmlSarOfficerReviewedLedgerEntry.class::cast)
                .min(HUMAN_FIRST_LATEST_SEQ)
                .map(e -> InvestigationOutcome.fromReviewDecision(e.reviewDecision, e.rejectionReason))
                .orElse(null);
    }
}
