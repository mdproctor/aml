package io.casehub.aml.compliance;

import io.casehub.aml.ledger.AmlInvestigationLedgerEntry;
import io.casehub.aml.trust.AmlTrustAttestationRepository;
import io.casehub.aml.trust.AmlTrustRoutingAttestation;
import io.casehub.aml.trust.AmlWorkerDecisionRepository;
import io.casehub.ledger.model.WorkerDecisionEntry;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.ledger.runtime.service.model.InclusionProof;
import io.casehub.ledger.runtime.service.model.ProofStep;
import io.casehub.work.runtime.model.WorkItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Layer 7: assembles compliance evidence for an AML investigation case.
 *
 * <p>Collects data from the Merkle-chained ledger (Layer 4), WorkItem SLA (Layer 2),
 * trust routing attestations (Layer 6/7), and GDPR erasure capability (Layer 4)
 * to produce a {@link ComplianceEvidence} record that maps each FinCEN/FATF
 * requirement to its current status.
 */
@ApplicationScoped
public class AmlComplianceEvidenceService {

    private final LedgerEntryRepository ledgerRepo;
    private final LedgerVerificationService verificationService;
    private final AmlTrustAttestationRepository attestationRepo;
    private final AmlWorkerDecisionRepository workerDecisionRepo;
    private final EntityManager em;

    @Inject
    public AmlComplianceEvidenceService(
            LedgerEntryRepository ledgerRepo,
            LedgerVerificationService verificationService,
            AmlTrustAttestationRepository attestationRepo,
            AmlWorkerDecisionRepository workerDecisionRepo,
            EntityManager em) {
        this.ledgerRepo = ledgerRepo;
        this.verificationService = verificationService;
        this.attestationRepo = attestationRepo;
        this.workerDecisionRepo = workerDecisionRepo;
        this.em = em;
    }

    /**
     * Returns compliance evidence for the given case, or empty if no AML ledger entries exist.
     */
    public Optional<ComplianceEvidence> findEvidence(UUID caseId) {
        List<AmlInvestigationLedgerEntry> amlEntries =
                filterAmlEntries(ledgerRepo.findBySubjectId(caseId));
        if (amlEntries.isEmpty()) return Optional.empty();
        return Optional.of(build(caseId, amlEntries));
    }

    /**
     * Assembles full compliance evidence for the given case.
     * Package-private for direct unit test access (bypasses the double-query in findEvidence).
     */
    ComplianceEvidence assembleEvidence(UUID caseId) {
        return build(caseId, filterAmlEntries(ledgerRepo.findBySubjectId(caseId)));
    }

    private ComplianceEvidence build(UUID caseId, List<AmlInvestigationLedgerEntry> amlEntries) {
        return new ComplianceEvidence(
                caseId,
                Instant.now(),
                buildAuditChain(caseId, amlEntries),
                buildSla(amlEntries),
                buildTrustRouting(caseId),
                buildGdprErasure(),
                null // signature — reserved for future offline signing
        );
    }

    // -- Audit chain -----------------------------------------------------------

    private AuditChainRequirement buildAuditChain(UUID caseId,
            List<AmlInvestigationLedgerEntry> amlEntries) {
        if (amlEntries.isEmpty()) {
            return new AuditChainRequirement(
                    AuditChainRequirement.REQUIREMENT_ID,
                    AuditChainRequirement.CITATION,
                    AuditChainRequirement.MECHANISM,
                    RequirementStatus.GAP, null, false, List.of());
        }

        boolean chainVerified = false;
        String treeRoot = null;
        try {
            chainVerified = verificationService.verify(caseId);
            treeRoot = verificationService.treeRoot(caseId);
        } catch (IllegalStateException ignored) {
            // No Merkle frontier exists — chain cannot be verified
        }

        List<LedgerEventRecord> events = new ArrayList<>();
        for (AmlInvestigationLedgerEntry entry : amlEntries) {
            AmlInclusionProof proof = buildInclusionProof(entry.id);
            events.add(new LedgerEventRecord(
                    entry.id, entry.eventType, entry.actorId, entry.actorRole,
                    entry.occurredAt, entry.causedByEntryId, entry.digest, proof));
        }

        boolean allReviewsLinked = amlEntries.stream()
                .filter(e -> "COMPLIANCE_REVIEW_OPENED".equals(e.eventType))
                .allMatch(e -> e.causedByEntryId != null);

        RequirementStatus status;
        if (chainVerified && allReviewsLinked) {
            status = RequirementStatus.CLOSED;
        } else if (!amlEntries.isEmpty()) {
            status = RequirementStatus.PARTIAL;
        } else {
            status = RequirementStatus.GAP;
        }

        return new AuditChainRequirement(
                AuditChainRequirement.REQUIREMENT_ID,
                AuditChainRequirement.CITATION,
                AuditChainRequirement.MECHANISM,
                status, treeRoot, chainVerified, events);
    }

    private AmlInclusionProof buildInclusionProof(UUID entryId) {
        try {
            InclusionProof proof = verificationService.inclusionProof(entryId);
            List<AmlProofStep> steps = proof.siblings().stream()
                    .map(s -> new AmlProofStep(s.hash(), s.side().name()))
                    .toList();
            return new AmlInclusionProof(
                    proof.entryIndex(), proof.treeSize(),
                    proof.leafHash(), steps, proof.treeRoot());
        } catch (Exception ignored) {
            return null;
        }
    }

    // -- SLA -------------------------------------------------------------------

    private SlaRequirement buildSla(List<AmlInvestigationLedgerEntry> amlEntries) {
        Optional<AmlInvestigationLedgerEntry> reviewEntry = amlEntries.stream()
                .filter(e -> "COMPLIANCE_REVIEW_OPENED".equals(e.eventType))
                .findFirst();

        if (reviewEntry.isEmpty()) {
            return new SlaRequirement(
                    SlaRequirement.REQUIREMENT_ID,
                    SlaRequirement.CITATION,
                    SlaRequirement.MECHANISM,
                    RequirementStatus.GAP,
                    null, null, null, false, List.of(),
                    SlaRequirement.ESCALATION_POLICY);
        }

        UUID taskId;
        try {
            taskId = UUID.fromString(reviewEntry.get().transactionId);
        } catch (IllegalArgumentException e) {
            return new SlaRequirement(
                    SlaRequirement.REQUIREMENT_ID,
                    SlaRequirement.CITATION,
                    SlaRequirement.MECHANISM,
                    RequirementStatus.PARTIAL,
                    null, null, null, false, List.of(),
                    SlaRequirement.ESCALATION_POLICY);
        }

        WorkItem wi = em.find(WorkItem.class, taskId);
        if (wi == null) {
            return new SlaRequirement(
                    SlaRequirement.REQUIREMENT_ID,
                    SlaRequirement.CITATION,
                    SlaRequirement.MECHANISM,
                    RequirementStatus.PARTIAL,
                    taskId, null, null, false, List.of(),
                    SlaRequirement.ESCALATION_POLICY);
        }

        Instant claimDeadline = wi.claimDeadline;
        Instant completedAt = wi.completedAt;
        boolean slaMet = completedAt != null && claimDeadline != null
                && completedAt.isBefore(claimDeadline);

        List<String> candidateGroups = wi.candidateGroups != null
                ? Arrays.asList(wi.candidateGroups.split(","))
                : List.of();

        RequirementStatus status;
        if (claimDeadline == null) {
            status = RequirementStatus.PARTIAL;     // WorkItem found but deadline unset
        } else if (completedAt == null && Instant.now().isAfter(claimDeadline)) {
            status = RequirementStatus.BREACHED;    // deadline passed, officer hasn't acted
        } else if (completedAt != null && !slaMet) {
            status = RequirementStatus.BREACHED;    // completed after deadline
        } else if (completedAt == null) {
            status = RequirementStatus.PARTIAL;     // within deadline, officer hasn't acted yet
        } else {
            status = RequirementStatus.CLOSED;      // completed before deadline
        }

        return new SlaRequirement(
                SlaRequirement.REQUIREMENT_ID,
                SlaRequirement.CITATION,
                SlaRequirement.MECHANISM,
                status, taskId, claimDeadline, completedAt, slaMet, candidateGroups,
                SlaRequirement.ESCALATION_POLICY);
    }

    // -- Trust routing ---------------------------------------------------------

    private TrustRoutingRequirement buildTrustRouting(UUID caseId) {
        List<WorkerDecisionEntry> dispatched = workerDecisionRepo.findAllByCaseId(caseId);
        Set<String> dispatchedCaps = dispatched.stream()
                .map(w -> w.capabilityTag)
                .collect(Collectors.toSet());

        List<AmlTrustRoutingAttestation> attestations =
                attestationRepo.findByInvestigationCaseId(caseId);
        Set<String> attestedCaps = attestations.stream()
                .map(a -> a.capabilityTag)
                .collect(Collectors.toSet());

        List<RoutingDecisionRecord> decisions = attestations.stream()
                .map(a -> new RoutingDecisionRecord(
                        a.capabilityTag, a.selectedWorkerId,
                        a.trustScoreAtRouting, a.thresholdApplied, a.id))
                .toList();

        RequirementStatus status;
        if (dispatchedCaps.isEmpty()) {
            status = RequirementStatus.GAP;
        } else if (attestedCaps.containsAll(dispatchedCaps)) {
            status = RequirementStatus.CLOSED;
        } else {
            status = RequirementStatus.PARTIAL;
        }

        return new TrustRoutingRequirement(
                TrustRoutingRequirement.REQUIREMENT_ID,
                TrustRoutingRequirement.CITATION,
                TrustRoutingRequirement.MECHANISM,
                status, decisions);
    }

    // -- GDPR erasure ----------------------------------------------------------

    private GdprErasureRequirement buildGdprErasure() {
        return new GdprErasureRequirement(
                GdprErasureRequirement.REQUIREMENT_ID,
                GdprErasureRequirement.CITATION,
                GdprErasureRequirement.MECHANISM,
                true,  // erasureCapabilityWired — LedgerErasureService is on classpath
                true,  // pseudonymizationActive
                GdprErasureRequirement.ERASURE_ENDPOINT);
    }

    // -- Internal helpers ------------------------------------------------------

    private List<AmlInvestigationLedgerEntry> filterAmlEntries(List<LedgerEntry> entries) {
        return entries.stream()
                .filter(AmlInvestigationLedgerEntry.class::isInstance)
                .map(AmlInvestigationLedgerEntry.class::cast)
                .toList();
    }
}
