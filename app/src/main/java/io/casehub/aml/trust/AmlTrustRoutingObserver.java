package io.casehub.aml.trust;

import io.casehub.aml.routing.AmlTrustRoutingPolicyProvider;
import io.casehub.engine.common.spi.event.WorkerDecisionEvent;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.routing.TrustScoreCache;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import java.time.Instant;
import java.util.UUID;

/**
 * Layer 7: writes AmlTrustRoutingAttestation on each WorkerDecisionEvent,
 * capturing the trust score from TrustScoreCache before it can drift.
 *
 * Uses @ObservesAsync to match how the engine fires WorkerDecisionEvent (async CDI).
 * Persists via AmlTrustAttestationRepository (qhorus EntityManager) rather than
 * LedgerEntryRepository to avoid Merkle frontier contention with the engine's own
 * ledger writers on the same subjectId. REQUIRES_NEW decouples each write from the
 * engine worker's transaction. Sequential nextSequenceNumber() is scoped to attestation
 * entries only; concurrent-write risk tracked as casehubio/aml#44.
 */
@ApplicationScoped
public class AmlTrustRoutingObserver {

    private static final String ACTOR_ID = "aml-orchestrator";
    private static final String ACTOR_ROLE = "AmlInvestigationOrchestrator";

    @Inject TrustScoreCache trustScoreCache;
    @Inject AmlTrustRoutingPolicyProvider policyProvider;
    @Inject AmlTrustAttestationRepository attestationRepo;

    @Transactional(TxType.REQUIRES_NEW)
    public void onWorkerDecision(@ObservesAsync WorkerDecisionEvent event) {
        final Double score = trustScoreCache
                .getCapabilityScore(event.workerId(), event.capabilityTag())
                .stream().boxed().findFirst().orElse(null);

        final double threshold = policyProvider.forCapability(event.capabilityTag()).threshold();
        final int seq = attestationRepo.nextSequenceNumber(event.caseId());

        final AmlTrustRoutingAttestation entry = new AmlTrustRoutingAttestation();
        entry.id = UUID.randomUUID();
        entry.subjectId = event.caseId();
        entry.investigationCaseId = event.caseId();
        entry.capabilityTag = event.capabilityTag();
        entry.selectedWorkerId = event.workerId();
        entry.trustScoreAtRouting = score;
        entry.thresholdApplied = threshold;
        entry.sequenceNumber = seq;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ACTOR_ID;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = ACTOR_ROLE;
        entry.occurredAt = Instant.now();
        attestationRepo.save(entry);
    }
}
