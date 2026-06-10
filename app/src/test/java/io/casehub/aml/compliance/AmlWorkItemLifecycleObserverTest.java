package io.casehub.aml.compliance;

import io.casehub.aml.ledger.AmlLedgerService;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AmlWorkItemLifecycleObserverTest {

    @Mock AmlLedgerService ledgerService;
    AmlWorkItemLifecycleObserver observer;

    UUID caseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        observer = new AmlWorkItemLifecycleObserver(ledgerService);
    }

    @Test
    void completed_validCallerRef_writesApproved() {
        observer.onWorkItemLifecycle(event(WorkItemStatus.COMPLETED, "aml:investigation:" + caseId,
                "compliance-officer-001"));

        verify(ledgerService).writeSarOfficerReviewed(eq(caseId), eq("compliance-officer-001"),
                eq("APPROVED"));
        verify(ledgerService, never()).writeSarOfficerReviewedFailure(any(), any());
    }

    @Test
    void rejected_validCallerRef_writesRejected() {
        observer.onWorkItemLifecycle(event(WorkItemStatus.REJECTED, "aml:investigation:" + caseId,
                "compliance-officer-001"));

        verify(ledgerService).writeSarOfficerReviewed(eq(caseId), eq("compliance-officer-001"),
                eq("REJECTED"));
    }

    @Test
    void inProgress_noWrite() {
        observer.onWorkItemLifecycle(event(WorkItemStatus.IN_PROGRESS, "aml:investigation:" + caseId,
                "officer"));

        verify(ledgerService, never()).writeSarOfficerReviewed(any(), any(), any());
        verify(ledgerService, never()).writeSarOfficerReviewedFailure(any(), any());
    }

    @Test
    void nullWorkItem_noWrite() {
        // Use fromWire() to construct an event without a WorkItem snapshot
        // (WorkItemLifecycleEvent.of() NPEs on null workItem)
        WorkItemLifecycleEvent event = WorkItemLifecycleEvent.fromWire(
                "io.casehub.work.workitem.completed",
                "/workitems/" + UUID.randomUUID(),
                UUID.randomUUID().toString(),
                UUID.randomUUID(), WorkItemStatus.COMPLETED,
                Instant.now(), "officer", null, null, null, null, null);
        observer.onWorkItemLifecycle(event);

        verify(ledgerService, never()).writeSarOfficerReviewed(any(), any(), any());
    }

    @Test
    void oldFormatCallerRef_noWrite() {
        observer.onWorkItemLifecycle(event(WorkItemStatus.COMPLETED,
                "aml:investigation/TXN-001", "officer"));

        verify(ledgerService, never()).writeSarOfficerReviewed(any(), any(), any());
    }

    @Test
    void differentDomain_noWrite() {
        observer.onWorkItemLifecycle(event(WorkItemStatus.COMPLETED,
                "devtown:pr-review:" + UUID.randomUUID(), "actor"));

        verify(ledgerService, never()).writeSarOfficerReviewed(any(), any(), any());
    }

    @Test
    void invalidUuidInCallerRef_noWrite() {
        observer.onWorkItemLifecycle(event(WorkItemStatus.COMPLETED,
                "aml:investigation:not-a-uuid", "officer"));

        verify(ledgerService, never()).writeSarOfficerReviewed(any(), any(), any());
    }

    @Test
    void nullActor_fallsBackToUnknown() {
        observer.onWorkItemLifecycle(event(WorkItemStatus.COMPLETED,
                "aml:investigation:" + caseId, null));

        verify(ledgerService).writeSarOfficerReviewed(eq(caseId), eq("unknown-officer"),
                eq("APPROVED"));
    }

    @Test
    void ledgerWriteFails_writesFailureEntry() {
        doThrow(new RuntimeException("DB error"))
            .when(ledgerService).writeSarOfficerReviewed(any(), any(), any());

        observer.onWorkItemLifecycle(event(WorkItemStatus.COMPLETED,
                "aml:investigation:" + caseId, "officer-X"));

        verify(ledgerService).writeSarOfficerReviewedFailure(eq(caseId), eq("officer-X"));
    }

    // -- Helpers --

    private WorkItemLifecycleEvent event(WorkItemStatus status, String callerRef, String actor) {
        WorkItem wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.status = status;
        wi.callerRef = callerRef;
        return WorkItemLifecycleEvent.of(status.name(), wi, actor, null);
    }
}
