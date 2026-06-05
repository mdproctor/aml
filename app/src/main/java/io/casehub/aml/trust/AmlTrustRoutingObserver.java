package io.casehub.aml.trust;

import io.casehub.aml.routing.AmlTrustRoutingPolicyProvider;
import io.casehub.engine.common.spi.event.WorkerDecisionEvent;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.routing.TrustScoreCache;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Layer 7: writes AmlTrustRoutingAttestation on each WorkerDecisionEvent,
 * capturing the trust score from TrustScoreCache before it can drift.
 *
 * <p>Uses @ObservesAsync to match how the engine fires WorkerDecisionEvent (async CDI).
 *
 * <p><b>Ledger subject isolation:</b> attestations use a dedicated subject UUID derived
 * from the case UUID (see {@link #attestationSubjectFor}). This keeps the attestation
 * ledger chain independent of the investigation chain — engine WorkerDecisionEntry,
 * AML case-opened and compliance-review entries all use caseId as subject, so using
 * caseId here would cause sequence number conflicts on IDX_LEDGER_ENTRY_SUBJECT_SEQ.
 *
 * <p><b>Concurrency:</b> pattern-analysis, osint-screening, and senior-analyst can all
 * dispatch simultaneously for a PEP case, firing three concurrent WorkerDecisionEvents.
 * Per-subject synchronization ensures each REQUIRES_NEW transaction (inside
 * {@link AmlTrustAttestationRepository#saveWithSequence}) commits and releases the
 * lock before the next call reads the sequence max. This prevents two writers from
 * both seeing max=null and both assigning seq=1.
 */
@ApplicationScoped
public class AmlTrustRoutingObserver {

    private static final String ACTOR_ID = "aml-orchestrator";
    private static final String ACTOR_ROLE = "AmlInvestigationOrchestrator";

    private final ConcurrentHashMap<UUID, Object> subjectLocks = new ConcurrentHashMap<>();

    @Inject TrustScoreCache trustScoreCache;
    @Inject AmlTrustRoutingPolicyProvider policyProvider;
    @Inject AmlTrustAttestationRepository attestationRepo;

    public void onWorkerDecision(@ObservesAsync WorkerDecisionEvent event) {
        final Double score = trustScoreCache
                .getCapabilityScore(event.workerId(), event.capabilityTag())
                .stream().boxed().findFirst().orElse(null);

        final double threshold = policyProvider.forCapability(event.capabilityTag()).threshold();
        final UUID attestationSubject = attestationSubjectFor(event.caseId());

        final AmlTrustRoutingAttestation entry = new AmlTrustRoutingAttestation();
        entry.id = UUID.randomUUID();
        entry.subjectId = attestationSubject;
        entry.investigationCaseId = event.caseId();
        entry.capabilityTag = event.capabilityTag();
        entry.selectedWorkerId = event.workerId();
        entry.trustScoreAtRouting = score;
        entry.thresholdApplied = threshold;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ACTOR_ID;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = ACTOR_ROLE;
        entry.occurredAt = Instant.now();

        // Acquire per-subject lock before starting the transaction. The lock is released
        // only after REQUIRES_NEW commits (saveWithSequence returns), preventing concurrent
        // observers from reading the same max-sequence before any entry is committed.
        final Object lock = subjectLocks.computeIfAbsent(attestationSubject, k -> new Object());
        synchronized (lock) {
            attestationRepo.saveWithSequence(entry);
        }
    }

    /**
     * Derives a stable attestation-specific ledger subject from the investigation case UUID.
     * Namespaced to ensure it never collides with the case subject used by investigation entries.
     */
    static UUID attestationSubjectFor(UUID caseId) {
        return UUID.nameUUIDFromBytes(
                ("aml-trust-routing-attestation:" + caseId).getBytes(StandardCharsets.UTF_8));
    }
}
