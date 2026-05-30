package io.casehub.aml.compliance;

import io.casehub.aml.ledger.AmlInvestigationLedgerEntry;
import io.casehub.aml.trust.AmlTrustAttestationRepository;
import io.casehub.aml.trust.AmlTrustRoutingAttestation;
import io.casehub.aml.trust.AmlWorkerDecisionRepository;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.model.WorkerDecisionEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.ledger.runtime.service.model.InclusionProof;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AmlComplianceEvidenceServiceTest {

    @Mock LedgerEntryRepository ledgerRepo;
    @Mock LedgerVerificationService verificationService;
    @Mock AmlTrustAttestationRepository attestationRepo;
    @Mock AmlWorkerDecisionRepository workerDecisionRepo;
    @Mock EntityManager em;

    AmlComplianceEvidenceService service;

    UUID caseId = UUID.randomUUID();
    UUID caseOpenedId = UUID.randomUUID();
    UUID reviewOpenedId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    String treeRoot = "sha256:abc123";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AmlComplianceEvidenceService(
            ledgerRepo, verificationService, attestationRepo,
            workerDecisionRepo, em);
    }

    @Test
    void assembleEvidence_happyPath_allRequirementsClosed() {
        var opened = caseOpenedEntry(caseId, caseOpenedId);
        var review = reviewOpenedEntry(caseId, reviewOpenedId, taskId, caseOpenedId);
        when(ledgerRepo.findBySubjectId(caseId)).thenReturn(List.of(opened, review));
        when(verificationService.verify(caseId)).thenReturn(true);
        when(verificationService.treeRoot(caseId)).thenReturn(treeRoot);
        when(verificationService.inclusionProof(any())).thenReturn(stubProof());
        WorkItem wi = workItem(taskId, Instant.now().plus(30, ChronoUnit.DAYS), null);
        when(em.find(eq(WorkItem.class), eq(taskId))).thenReturn(wi);
        var att1 = attestation(caseId, "entity-resolution", "agent-A", 0.8, 0.70);
        var att2 = attestation(caseId, "sar-drafting", "agent-B", 0.9, 0.75);
        when(attestationRepo.findByInvestigationCaseId(caseId)).thenReturn(List.of(att1, att2));
        var wd1 = workerDecision(caseId, "entity-resolution", "agent-A");
        var wd2 = workerDecision(caseId, "sar-drafting", "agent-B");
        when(workerDecisionRepo.findAllByCaseId(caseId)).thenReturn(List.of(wd1, wd2));

        ComplianceEvidence evidence = service.assembleEvidence(caseId);

        assertEquals(caseId, evidence.caseId());
        assertNotNull(evidence.generatedAt());
        assertNull(evidence.signature());
        assertEquals(RequirementStatus.CLOSED, evidence.auditChain().status());
        assertTrue(evidence.auditChain().chainVerified());
        assertEquals(treeRoot, evidence.auditChain().treeRoot());
        assertEquals(2, evidence.auditChain().events().size());
        assertNull(evidence.auditChain().events().get(0).causedByEntryId());
        assertEquals(caseOpenedId, evidence.auditChain().events().get(1).causedByEntryId());
        // WorkItem open (completedAt=null) with deadline 30 days away — officer hasn't acted yet.
        // slaMet=false (not completed), status=PARTIAL (mechanism present, evidence incomplete).
        assertFalse(evidence.sla().slaMet());
        assertEquals(RequirementStatus.PARTIAL, evidence.sla().status());
        assertNotNull(evidence.sla().workItemId());
        assertEquals(RequirementStatus.CLOSED, evidence.trustRouting().status());
        assertEquals(2, evidence.trustRouting().decisions().size());
        assertTrue(evidence.gdprErasure().erasureCapabilityWired());
    }

    @Test
    void assembleEvidence_chainVerifiedFalse_auditChainPartial() {
        var opened = caseOpenedEntry(caseId, caseOpenedId);
        var review = reviewOpenedEntry(caseId, reviewOpenedId, taskId, caseOpenedId);
        when(ledgerRepo.findBySubjectId(caseId)).thenReturn(List.of(opened, review));
        when(verificationService.verify(caseId)).thenReturn(false);
        when(verificationService.treeRoot(caseId)).thenReturn(treeRoot);
        when(verificationService.inclusionProof(any())).thenReturn(stubProof());
        when(em.find(eq(WorkItem.class), eq(taskId))).thenReturn(workItem(taskId, Instant.now().plus(30, ChronoUnit.DAYS), null));
        when(attestationRepo.findByInvestigationCaseId(caseId)).thenReturn(List.of());
        when(workerDecisionRepo.findAllByCaseId(caseId)).thenReturn(List.of());

        assertEquals(RequirementStatus.PARTIAL, service.assembleEvidence(caseId).auditChain().status());
    }

    @Test
    void assembleEvidence_slaBreached_statusBreached() {
        var opened = caseOpenedEntry(caseId, caseOpenedId);
        var review = reviewOpenedEntry(caseId, reviewOpenedId, taskId, caseOpenedId);
        when(ledgerRepo.findBySubjectId(caseId)).thenReturn(List.of(opened, review));
        when(verificationService.verify(caseId)).thenReturn(true);
        when(verificationService.treeRoot(caseId)).thenReturn(treeRoot);
        when(verificationService.inclusionProof(any())).thenReturn(stubProof());
        when(attestationRepo.findByInvestigationCaseId(caseId)).thenReturn(List.of());
        when(workerDecisionRepo.findAllByCaseId(caseId)).thenReturn(List.of());
        Instant deadline = Instant.now().minus(1, ChronoUnit.DAYS);
        when(em.find(eq(WorkItem.class), eq(taskId))).thenReturn(workItem(taskId, deadline, null));

        assertEquals(RequirementStatus.BREACHED, service.assembleEvidence(caseId).sla().status());
    }

    @Test
    void assembleEvidence_partialAttestations_trustRoutingPartial() {
        var opened = caseOpenedEntry(caseId, caseOpenedId);
        var review = reviewOpenedEntry(caseId, reviewOpenedId, taskId, caseOpenedId);
        when(ledgerRepo.findBySubjectId(caseId)).thenReturn(List.of(opened, review));
        when(verificationService.verify(caseId)).thenReturn(true);
        when(verificationService.treeRoot(caseId)).thenReturn(treeRoot);
        when(verificationService.inclusionProof(any())).thenReturn(stubProof());
        when(em.find(eq(WorkItem.class), eq(taskId))).thenReturn(workItem(taskId, Instant.now().plus(30, ChronoUnit.DAYS), null));
        when(attestationRepo.findByInvestigationCaseId(caseId)).thenReturn(List.of(
            attestation(caseId, "entity-resolution", "agent-A", 0.8, 0.70)));
        when(workerDecisionRepo.findAllByCaseId(caseId)).thenReturn(List.of(
            workerDecision(caseId, "entity-resolution", "agent-A"),
            workerDecision(caseId, "sar-drafting", "agent-B")));

        assertEquals(RequirementStatus.PARTIAL, service.assembleEvidence(caseId).trustRouting().status());
    }

    @Test
    void findEvidence_noLedgerEntries_returnsEmpty() {
        when(ledgerRepo.findBySubjectId(caseId)).thenReturn(List.of());
        assertTrue(service.findEvidence(caseId).isEmpty());
    }

    // -- Helpers ---------------------------------------------------------------

    private AmlInvestigationLedgerEntry caseOpenedEntry(UUID caseId, UUID entryId) {
        var e = new AmlInvestigationLedgerEntry();
        e.id = entryId; e.subjectId = caseId; e.sequenceNumber = 1;
        e.eventType = "CASE_OPENED"; e.transactionId = "TXN-001";
        e.entryType = LedgerEntryType.EVENT; e.actorId = "aml-orchestrator";
        e.actorType = ActorType.SYSTEM; e.actorRole = "AmlInvestigationOrchestrator";
        e.occurredAt = Instant.now().minus(5, ChronoUnit.MINUTES);
        e.digest = "sha256:abc"; e.causedByEntryId = null;
        return e;
    }

    private AmlInvestigationLedgerEntry reviewOpenedEntry(UUID caseId, UUID entryId,
            UUID taskId, UUID causedBy) {
        var e = new AmlInvestigationLedgerEntry();
        e.id = entryId; e.subjectId = caseId; e.sequenceNumber = 2;
        e.eventType = "COMPLIANCE_REVIEW_OPENED"; e.transactionId = taskId.toString();
        e.entryType = LedgerEntryType.EVENT; e.actorId = "aml-orchestrator";
        e.actorType = ActorType.SYSTEM; e.actorRole = "AmlInvestigationOrchestrator";
        e.occurredAt = Instant.now(); e.digest = "sha256:def"; e.causedByEntryId = causedBy;
        return e;
    }

    private WorkItem workItem(UUID taskId, Instant deadline, Instant completedAt) {
        var wi = new WorkItem();
        wi.id = taskId;
        wi.status = completedAt != null ? WorkItemStatus.COMPLETED : WorkItemStatus.PENDING;
        wi.claimDeadline = deadline;
        wi.completedAt = completedAt;
        wi.candidateGroups = "compliance-officers";
        return wi;
    }

    private AmlTrustRoutingAttestation attestation(UUID caseId, String cap, String worker,
            double score, double threshold) {
        var a = new AmlTrustRoutingAttestation();
        a.id = UUID.randomUUID(); a.investigationCaseId = caseId;
        a.capabilityTag = cap; a.selectedWorkerId = worker;
        a.trustScoreAtRouting = score; a.thresholdApplied = threshold;
        a.occurredAt = Instant.now();
        return a;
    }

    private WorkerDecisionEntry workerDecision(UUID caseId, String cap, String worker) {
        var wd = new WorkerDecisionEntry();
        wd.caseId = caseId; wd.capabilityTag = cap; wd.workerId = worker;
        return wd;
    }

    private InclusionProof stubProof() {
        return new InclusionProof(UUID.randomUUID(), 0, 2, "sha256:leaf", List.of(), "sha256:root");
    }
}
