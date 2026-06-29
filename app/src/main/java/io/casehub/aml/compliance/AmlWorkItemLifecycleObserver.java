package io.casehub.aml.compliance;

import io.casehub.aml.ledger.AmlLedgerService;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.api.WorkItemStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Layer 9: writes an AML_SAR_OFFICER_REVIEWED ledger entry when the compliance officer
 * completes or rejects the SAR review WorkItem.
 *
 * <p>Observes {@link WorkItemLifecycleEvent} asynchronously. Only handles WorkItems whose
 * {@code callerRef} matches the {@code "aml:investigation:<UUID>"} prefix — set by
 * {@link io.casehub.aml.ComplianceReviewLifecycle} on WorkItem creation.
 *
 * <p>Both {@link WorkItemStatus#COMPLETED} (approved) and {@link WorkItemStatus#REJECTED}
 * produce a ledger entry with the officer's actorId. This officer identity is the human PII
 * that GDPR Art.17 erasure acts on.
 *
 * <p>Applies the PP-20260530-49856c double-try/catch pattern: failure writes an
 * observer-failure record via REQUIRES_NEW so it commits independently.
 */
@ApplicationScoped
public class AmlWorkItemLifecycleObserver {

    private static final Logger LOG = Logger.getLogger(AmlWorkItemLifecycleObserver.class);
    private static final String CALLER_REF_PREFIX = "aml:investigation:";

    private final AmlLedgerService ledgerService;

    @Inject
    public AmlWorkItemLifecycleObserver(AmlLedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    public void onWorkItemLifecycle(@ObservesAsync WorkItemLifecycleEvent event) {
        // Guard 1: only handle terminal officer decisions
        if (event.status() != WorkItemStatus.COMPLETED
                && event.status() != WorkItemStatus.REJECTED) {
            return;
        }

        // Guard 2: workItem snapshot must be present (null in cross-cluster wire events)
        final WorkItem workItem = event.workItem();
        if (workItem == null) {
            LOG.warnf("WorkItemLifecycleEvent has no WorkItem snapshot — cannot write SAR_OFFICER_REVIEWED");
            return;
        }

        // Guard 3: only handle AML compliance review WorkItems
        final String callerRef = workItem.callerRef;
        if (callerRef == null || !callerRef.startsWith(CALLER_REF_PREFIX)) {
            return;
        }

        // Guard 4: parse caseId — invalid UUID means data corruption, log and skip
        final UUID caseId;
        try {
            caseId = UUID.fromString(callerRef.substring(CALLER_REF_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid caseId in callerRef '%s' — skipping SAR_OFFICER_REVIEWED write",
                    callerRef);
            return;
        }

        final String officerId = event.actor() != null ? event.actor() : "unknown-officer";
        final String reviewDecision = event.status() == WorkItemStatus.COMPLETED
                ? "APPROVED" : "REJECTED";
        final String rejectionReason = event.status() == WorkItemStatus.REJECTED
                ? event.detail() : null;

        boolean written = false;
        try {
            ledgerService.writeSarOfficerReviewed(caseId, officerId, reviewDecision, rejectionReason);
            written = true;
        } catch (Exception e) {
            LOG.warnf(e, "Failed to write SAR_OFFICER_REVIEWED for caseId=%s officer=%s",
                    caseId, officerId);
            if (!written) {
                try {
                    ledgerService.writeSarOfficerReviewedFailure(caseId, officerId, reviewDecision, rejectionReason);
                } catch (Exception inner) {
                    LOG.errorf(inner,
                            "AUDIT GAP: SAR_OFFICER_REVIEWED failure entry also failed caseId=%s",
                            caseId);
                }
            }
        }
    }
}
