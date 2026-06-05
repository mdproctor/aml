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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Layer 7: writes AmlTrustRoutingAttestation on each WorkerDecisionEvent,
 * capturing the trust score from TrustScoreCache before it can drift.
 *
 * <p>Uses @ObservesAsync to match how the engine fires WorkerDecisionEvent (async CDI).
 * REQUIRES_NEW decouples each attestation write from the engine worker's transaction.
 *
 * <p><b>Ledger subject isolation:</b> attestations use a dedicated subject UUID derived
 * from the case UUID rather than the case UUID directly. This keeps the attestation
 * ledger chain independent of the investigation chain (engine WorkerDecisionEntry,
 * AML case-opened, compliance-review entries all use caseId as subject). Without this
 * separation, nextSequenceNumber() would conflict with entries already written by the
 * ledger service and the engine for the same caseId subject.
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
        final UUID attestationSubject = attestationSubjectFor(event.caseId());
        final int seq = attestationRepo.nextSequenceNumber(attestationSubject);

        final AmlTrustRoutingAttestation entry = new AmlTrustRoutingAttestation();
        entry.id = UUID.randomUUID();
        entry.subjectId = attestationSubject;
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

    /**
     * Derives a stable attestation-specific ledger subject from the investigation case UUID.
     * Namespaced to ensure it never collides with the case subject used by investigation
     * entries (WorkerDecisionEntry, AmlCaseOpenedLedgerEntry, etc.).
     */
    static UUID attestationSubjectFor(UUID caseId) {
        return UUID.nameUUIDFromBytes(
                ("aml-trust-routing-attestation:" + caseId).getBytes(StandardCharsets.UTF_8));
    }
}
