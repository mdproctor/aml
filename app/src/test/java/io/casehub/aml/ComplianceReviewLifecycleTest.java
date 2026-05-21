package io.casehub.aml;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.InvestigationSummary;
import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SpecialistOutcome;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemCreateRequest;
import io.casehub.work.runtime.model.WorkItemPriority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplianceReviewLifecycleTest {

    private final SuspiciousTransaction tx = new SuspiciousTransaction(
            "TXN-CLR", "ACC-A", "ACC-B",
            new BigDecimal("75000"), "USD",
            Instant.parse("2024-06-01T00:00:00Z"), "Test");

    private final InvestigationSummary summary = new InvestigationSummary(
            tx,
            new SpecialistOutcome.Completed<>(new EntityResolutionResult("E-1", "chain")),
            new SpecialistOutcome.Completed<>(new PatternAnalysisResult(true, "structuring")),
            new SpecialistOutcome.Declined<>("agent", "osint-screening", "clearance"),
            "narrative");

    private static WorkItem workItemWith(UUID id) {
        WorkItem wi = new WorkItem();
        wi.id = id;
        return wi;
    }

    @Test
    void openReview_creates30DayClaimDeadline() {
        AtomicReference<WorkItemCreateRequest> captured = new AtomicReference<>();
        ComplianceReviewLifecycle lifecycle = new ComplianceReviewLifecycle(request -> {
            captured.set(request);
            return workItemWith(UUID.randomUUID());
        });

        lifecycle.openReview(tx, summary);

        WorkItemCreateRequest req = captured.get();
        assertNotNull(req);
        assertEquals(WorkItemPriority.HIGH, req.priority);
        assertEquals("compliance-officers", req.candidateGroups);
        assertTrue(req.callerRef.contains("TXN-CLR"));

        long days = Duration.between(Instant.now(), req.claimDeadline).toDays();
        assertTrue(days >= 29 && days <= 30, "claimDeadline should be ~30 days, was " + days);
    }

    @Test
    void openReview_returnsWorkItemId() {
        UUID expectedId = UUID.randomUUID();
        ComplianceReviewLifecycle lifecycle = new ComplianceReviewLifecycle(
                request -> workItemWith(expectedId));

        String taskId = lifecycle.openReview(tx, summary);
        assertEquals(expectedId.toString(), taskId);
    }
}
