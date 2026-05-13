package io.casehub.aml.tutorial;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.aml.AmlInvestigationApplicationService;
import io.casehub.aml.domain.AmlInvestigationResult;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.work.runtime.model.WorkItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class WorkItemAmlInvestigationServiceTest {

    @Inject
    AmlInvestigationApplicationService service;

    private SuspiciousTransaction tx(String id) {
        return new SuspiciousTransaction(
                id, "ACC-A", "ACC-B",
                new BigDecimal("100000"), "USD",
                Instant.parse("2024-03-15T10:00:00Z"), "Structuring");
    }

    @Test
    @Transactional
    void investigate_createsWorkItemWithComplianceFields() {
        AmlInvestigationResult result = service.investigate(tx("TXN-LAYER2"));

        assertNotNull(result.complianceReviewTaskId(),
                "Layer 2 must return a complianceReviewTaskId");

        WorkItem item = WorkItem.findById(UUID.fromString(result.complianceReviewTaskId()));
        assertNotNull(item, "WorkItem must be persisted");
        assertTrue(item.candidateGroups.contains("compliance-officers"),
                "Must be routed to compliance-officers");
        assertTrue(item.claimDeadline.isAfter(Instant.now().plus(29, ChronoUnit.DAYS)),
                "claimDeadline must be ~30 days out");
        assertTrue(item.callerRef.contains("TXN-LAYER2"),
                "callerRef must reference the transaction ID");
    }

    @Test
    @Transactional
    void investigate_returnsSummaryWithAllFindings() {
        AmlInvestigationResult result = service.investigate(tx("TXN-LAYER2-B"));

        assertNotNull(result.summary());
        assertNotNull(result.summary().entityResolution());
        assertNotNull(result.summary().patternAnalysis());
        assertNotNull(result.summary().osintScreening());
        assertNotNull(result.summary().sarNarrative());
        assertEquals("TXN-LAYER2-B", result.summary().transaction().id());
    }
}
