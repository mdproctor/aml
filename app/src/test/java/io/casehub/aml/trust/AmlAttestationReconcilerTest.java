package io.casehub.aml.trust;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.model.WorkerDecisionEntry;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AmlAttestationReconcilerTest {

    @Mock AmlTrustAttestationRepository attestationRepo;
    AmlAttestationReconciler reconciler;
    UUID caseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        reconciler = new AmlAttestationReconciler(attestationRepo);
        when(attestationRepo.saveWithSequence(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void reconcileIfNeeded_missingAttestation_writesReconstructedEntry() {
        var dispatched = List.of(
            workerDecision("entity-resolution", "agent-A", 0.8, 0.7),
            workerDecision("sar-drafting", "agent-B", 0.9, 0.75),
            workerDecision("osint-screening", "agent-C", null, null)
        );
        var existing = List.of(
            attestation("entity-resolution", "agent-A"),
            attestation("sar-drafting", "agent-B")
        );

        List<AmlTrustRoutingAttestation> result =
            reconciler.reconcileIfNeeded(caseId, dispatched, existing);

        assertEquals(3, result.size(), "Should have 3 total: 2 existing + 1 reconstructed");
        ArgumentCaptor<AmlTrustRoutingAttestation> captor =
            ArgumentCaptor.forClass(AmlTrustRoutingAttestation.class);
        verify(attestationRepo).saveWithSequence(captor.capture());
        AmlTrustRoutingAttestation saved = captor.getValue();
        assertEquals("osint-screening", saved.capabilityTag);
        assertEquals("agent-C", saved.selectedWorkerId);
        assertTrue(saved.reconstructed);
        assertFalse(saved.observerFailed);
        assertNull(saved.trustScoreAtRouting, "Phase-0 case: score is null");
        assertEquals(0.0, saved.thresholdApplied, "null threshold maps to 0.0");
    }

    @Test
    void reconcileIfNeeded_allCovered_noWrite() {
        var dispatched = List.of(workerDecision("entity-resolution", "agent-A", 0.8, 0.7));
        var existing = List.of(attestation("entity-resolution", "agent-A"));

        List<AmlTrustRoutingAttestation> result =
            reconciler.reconcileIfNeeded(caseId, dispatched, existing);

        assertEquals(1, result.size());
        verify(attestationRepo, never()).saveWithSequence(any());
    }

    @Test
    void reconcileIfNeeded_idempotent_noDuplicateWrite() {
        var dispatched = List.of(workerDecision("entity-resolution", "agent-A", 0.8, 0.7));
        // Existing already has a reconstructed entry for this capability
        var reconstructed = attestation("entity-resolution", "agent-A");
        reconstructed.reconstructed = true;
        var existing = List.of(reconstructed);

        reconciler.reconcileIfNeeded(caseId, dispatched, existing);
        reconciler.reconcileIfNeeded(caseId, dispatched, existing);

        verify(attestationRepo, never()).saveWithSequence(any());
    }

    @Test
    void reconcileIfNeeded_observerFailedEntry_treatedAsCovered_notReReconciled() {
        var dispatched = List.of(workerDecision("entity-resolution", "agent-A", 0.8, 0.7));
        var failureEntry = attestation("entity-resolution", "agent-A");
        failureEntry.observerFailed = true;
        var existing = List.of(failureEntry);

        List<AmlTrustRoutingAttestation> result =
            reconciler.reconcileIfNeeded(caseId, dispatched, existing);

        assertEquals(1, result.size(), "Observer-failed entry covers the capability; no new entry");
        verify(attestationRepo, never()).saveWithSequence(any());
    }

    @Test
    void reconcileIfNeeded_copiesTrustScoreFromWorkerDecisionEntry() {
        var dispatched = List.of(workerDecision("pattern-analysis", "agent-P", 0.72, 0.65));
        var existing = List.<AmlTrustRoutingAttestation>of();

        reconciler.reconcileIfNeeded(caseId, dispatched, existing);

        ArgumentCaptor<AmlTrustRoutingAttestation> captor =
            ArgumentCaptor.forClass(AmlTrustRoutingAttestation.class);
        verify(attestationRepo).saveWithSequence(captor.capture());
        AmlTrustRoutingAttestation saved = captor.getValue();
        assertEquals(0.72, saved.trustScoreAtRouting);
        assertEquals(0.65, saved.thresholdApplied);
    }

    // -- Helpers --

    private WorkerDecisionEntry workerDecision(String cap, String worker,
            Double score, Double threshold) {
        var e = new WorkerDecisionEntry();
        e.capabilityTag = cap;
        e.workerId = worker;
        e.caseId = caseId;
        e.trustScoreAtRouting = score;
        e.thresholdApplied = threshold;
        e.tenancyId = "default";
        e.subjectId = caseId;
        e.id = UUID.randomUUID();
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = worker;
        e.actorType = ActorType.SYSTEM;
        e.actorRole = "engine";
        e.occurredAt = Instant.now();
        e.sequenceNumber = 1;
        return e;
    }

    private AmlTrustRoutingAttestation attestation(String cap, String worker) {
        var a = new AmlTrustRoutingAttestation();
        a.id = UUID.randomUUID();
        a.capabilityTag = cap;
        a.selectedWorkerId = worker;
        a.investigationCaseId = caseId;
        a.trustScoreAtRouting = 0.8;
        a.thresholdApplied = 0.7;
        a.reconstructed = false;
        a.observerFailed = false;
        a.subjectId = AmlTrustRoutingObserver.attestationSubjectFor(caseId);
        a.entryType = LedgerEntryType.EVENT;
        a.actorId = "aml-orchestrator";
        a.actorType = ActorType.SYSTEM;
        a.actorRole = "AmlInvestigationOrchestrator";
        a.occurredAt = Instant.now();
        a.sequenceNumber = 1;
        return a;
    }
}
