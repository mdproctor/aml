package io.casehub.aml.domain;

public record AmlInvestigationResult(
        InvestigationSummary summary,
        String complianceReviewTaskId) {
}
