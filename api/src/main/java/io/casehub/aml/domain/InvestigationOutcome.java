package io.casehub.aml.domain;

import java.util.Objects;

public record InvestigationOutcome(String type, String reason) {

    public static InvestigationOutcome fromReviewDecision(
            final String reviewDecision, final String rejectionReason) {
        Objects.requireNonNull(reviewDecision,
                "reviewDecision must not be null — column is NOT NULL");
        return switch (reviewDecision) {
            case "APPROVED" -> new InvestigationOutcome("sar-filed", null);
            case "REJECTED" -> new InvestigationOutcome("gate-rejected", rejectionReason);
            case "UNKNOWN" -> new InvestigationOutcome("decision-not-recorded", null);
            default -> throw new IllegalStateException(
                    "Unexpected reviewDecision: " + reviewDecision);
        };
    }
}
