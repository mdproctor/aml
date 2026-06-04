package io.casehub.aml.trust;

import io.casehub.aml.domain.SarOutcome;
import io.casehub.aml.domain.SarVerdict;
import io.casehub.aml.engine.SarOutcomeRecordedEvent;
import jakarta.enterprise.event.Observes;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.model.WorkerDecisionEntry;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Records a SAR outcome as a {@link LedgerAttestation} on the worker decision entry
 * that performed the {@code sar-drafting} capability for the given case.
 *
 * <p>Called after a SAR has been reviewed externally (e.g. upheld by FinCEN, withdrawn,
 * or flagged as deficient). The attestation updates the trust record for the drafting
 * agent so that future trust-weighted routing prefers agents with better SAR outcomes.
 *
 * <p>Layer 6 tutorial component — maps SAR outcome feedback into the
 * {@code investigation-accuracy} trust dimension.
 */
@ApplicationScoped
public class SarOutcomeFeedbackService {

    private static final Logger LOG = Logger.getLogger(SarOutcomeFeedbackService.class);

    @PersistenceContext(unitName = "qhorus")
    EntityManager em;

    @Inject
    AmlWorkerDecisionRepository workerDecisionRepo;

    /**
     * Records a SAR outcome by writing a {@link LedgerAttestation} against the
     * {@code sar-drafting} worker decision entry for the given case.
     *
     * <p>If no {@code WorkerDecisionEntry} exists for the case (e.g. the SAR was
     * drafted outside the trust-routing path), the method logs a warning and returns
     * without throwing — callers are never blocked by missing history.
     *
     * @param caseId  the investigation case UUID
     * @param outcome the SAR outcome (verdict, reason, accuracy score)
     */
    @Transactional
    public void recordOutcome(final UUID caseId, final SarOutcome outcome) {
        final Optional<WorkerDecisionEntry> entryOpt =
                workerDecisionRepo.findLatestByCaseIdAndCapability(caseId, "sar-drafting");

        if (entryOpt.isEmpty()) {
            LOG.warnf("No WorkerDecisionEntry found for caseId=%s capability=sar-drafting — skipping attestation", caseId);
            return;
        }

        final WorkerDecisionEntry entry = entryOpt.get();
        final LedgerAttestation attestation = new LedgerAttestation();
        attestation.id = UUID.randomUUID();
        attestation.ledgerEntryId = entry.id;
        attestation.subjectId = caseId;
        attestation.attestorId = "aml-compliance-system";
        attestation.attestorType = ActorType.SYSTEM;
        attestation.attestorRole = "SarOutcomeFeedback";
        attestation.verdict = toVerdict(outcome.verdict());
        attestation.capabilityTag = "sar-drafting";
        attestation.trustDimension = "investigation-accuracy";
        attestation.dimensionScore = outcome.investigationAccuracyScore();
        attestation.confidence = 1.0;
        attestation.occurredAt = Instant.now();
        attestation.evidence = outcome.reason();

        em.persist(attestation);
    }

    public void onSarOutcome(@Observes SarOutcomeRecordedEvent event) {
        recordOutcome(event.caseId(), event.outcome());
    }

    private AttestationVerdict toVerdict(final SarVerdict verdict) {
        return switch (verdict) {
            case UPHELD -> AttestationVerdict.SOUND;
            case WITHDRAWN, FLAGGED -> AttestationVerdict.FLAGGED;
        };
    }
}
