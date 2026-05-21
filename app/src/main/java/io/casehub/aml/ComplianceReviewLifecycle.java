package io.casehub.aml;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.aml.domain.InvestigationSummary;
import io.casehub.aml.domain.SpecialistOutcome;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemCreateRequest;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.service.WorkItemService;

@ApplicationScoped
public class ComplianceReviewLifecycle {

    private final Function<WorkItemCreateRequest, WorkItem> creator;

    @Inject
    public ComplianceReviewLifecycle(WorkItemService workItemService) {
        this.creator = workItemService::create;
    }

    // Constructor for unit testing without CDI
    ComplianceReviewLifecycle(Function<WorkItemCreateRequest, WorkItem> creator) {
        this.creator = creator;
    }

    public String openReview(SuspiciousTransaction transaction, InvestigationSummary summary) {
        String osintNote = summary.osintScreening() instanceof SpecialistOutcome.Declined<?> d
                ? " OSINT declined: " + d.reason() + "." : "";
        WorkItem workItem = creator.apply(WorkItemCreateRequest.builder()
                .title("Compliance review — SAR for transaction " + transaction.id())
                .description(summary.sarNarrative() + osintNote)
                .category("aml-compliance")
                .priority(WorkItemPriority.HIGH)
                .candidateGroups("compliance-officers")
                .createdBy("aml-system")
                .claimDeadline(Instant.now().plus(30, ChronoUnit.DAYS))
                .callerRef("aml:investigation/" + transaction.id())
                .build());
        return workItem.id.toString();
    }
}
