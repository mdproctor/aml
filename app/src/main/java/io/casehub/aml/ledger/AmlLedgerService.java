package io.casehub.aml.ledger;

import java.time.Instant;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;

/**
 * Layer 4: writes AML domain-level ledger entries for each investigation lifecycle event.
 *
 * <p>Two entry types are written per investigation:
 * <ol>
 * <li>CASE_OPENED — at investigation start; subjectId = caseId</li>
 * <li>COMPLIANCE_REVIEW_OPENED — after the compliance officer WorkItem is created</li>
 * </ol>
 *
 * <p>The caseId UUID serves as the shared subjectId linking AML domain entries and
 * qhorus message entries (COMMAND/DONE/DECLINE per specialist) into one queryable chain.
 *
 * <p>All 8 required base fields are populated per GE-20260511-b6f903.
 */
@ApplicationScoped
public class AmlLedgerService {

    private static final String ACTOR_ID = "aml-orchestrator";
    private static final String ACTOR_ROLE = "AmlInvestigationOrchestrator";

    @Inject
    LedgerEntryRepository repository;

    /**
     * Write a CASE_OPENED entry and return its UUID — included in the HTTP response
     * so the caller can independently verify the investigation record.
     */
    public UUID writeCaseOpened(final SuspiciousTransaction transaction, final UUID caseId) {
        final int sequenceNumber = nextSequenceNumber(caseId);
        final AmlInvestigationLedgerEntry entry = new AmlInvestigationLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = caseId;
        entry.sequenceNumber = sequenceNumber;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ACTOR_ID;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = ACTOR_ROLE;
        entry.occurredAt = Instant.now();
        entry.transactionId = transaction.id();
        entry.eventType = "CASE_OPENED";
        repository.save(entry);
        return entry.id;
    }

    /**
     * Write a COMPLIANCE_REVIEW_OPENED entry after the SAR review WorkItem is created.
     *
     * <p>Sets {@code causedByEntryId} by querying for the {@code CASE_OPENED} entry for the
     * same caseId. This derivation happens inside the method so it works for both:
     * <ol>
     * <li>The synchronous Layer 3 path (AmlInvestigationCoordinator)</li>
     * <li>The async Layer 5 path — the sar-drafting worker lambda runs on a Quartz thread
     *     well after {@code startInvestigation()} returns, making parameter threading impractical</li>
     * </ol>
     */
    public void writeComplianceReviewOpened(final UUID caseId, final String taskId) {
        final UUID caseOpenedEntryId = repository.findBySubjectId(caseId).stream()
                .filter(e -> e instanceof AmlInvestigationLedgerEntry ale
                             && "CASE_OPENED".equals(ale.eventType))
                .map(e -> e.id)
                .findFirst()
                .orElse(null);
        final int sequenceNumber = nextSequenceNumber(caseId);
        final AmlInvestigationLedgerEntry entry = new AmlInvestigationLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = caseId;
        entry.sequenceNumber = sequenceNumber;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ACTOR_ID;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = ACTOR_ROLE;
        entry.occurredAt = Instant.now();
        entry.transactionId = taskId;
        entry.eventType = "COMPLIANCE_REVIEW_OPENED";
        entry.causedByEntryId = caseOpenedEntryId;
        repository.save(entry);
    }

    // NOTE: sequential, not concurrent-safe. Safe for Layer 4 where writes per caseId
    // are sequential. Layer 5+ parallel specialist ledger entries would need a DB-level
    // sequence or unique constraint on (subjectId, sequenceNumber).
    private int nextSequenceNumber(final UUID subjectId) {
        return repository.findLatestBySubjectId(subjectId)
                .map(e -> e.sequenceNumber + 1)
                .orElse(1);
    }

    /** No-op stub for unit tests that don't need ledger persistence. */
    public static AmlLedgerService noOp() {
        return new AmlLedgerService() {
            @Override public UUID writeCaseOpened(SuspiciousTransaction tx, UUID caseId) { return UUID.randomUUID(); }
            @Override public void writeComplianceReviewOpened(UUID caseId, String taskId) {}
        };
    }

    /** Stub that returns a fixed entryId from writeCaseOpened. */
    public static AmlLedgerService stub(final UUID entryId) {
        return new AmlLedgerService() {
            @Override public UUID writeCaseOpened(SuspiciousTransaction tx, UUID caseId) { return entryId; }
            @Override public void writeComplianceReviewOpened(UUID caseId, String taskId) {}
        };
    }
}
