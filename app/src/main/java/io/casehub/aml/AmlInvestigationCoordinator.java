package io.casehub.aml;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.aml.domain.AmlInvestigationResult;
import io.casehub.aml.domain.InvestigationSummary;
import io.casehub.aml.domain.SuspiciousTransaction;

@ApplicationScoped
public class AmlInvestigationCoordinator implements AmlInvestigationApplicationService {

    private final AmlInvestigator investigator;
    private final ComplianceReviewLifecycle compliance;

    @Inject
    public AmlInvestigationCoordinator(AmlInvestigator investigator, ComplianceReviewLifecycle compliance) {
        this.investigator = investigator;
        this.compliance = compliance;
    }

    @Override
    public AmlInvestigationResult investigate(SuspiciousTransaction transaction) {
        InvestigationSummary summary = investigator.investigate(transaction);
        String taskId = compliance.openReview(transaction, summary);
        return new AmlInvestigationResult(summary, taskId);
    }
}
