package io.casehub.aml.domain;

public record InvestigationOutcome(String type) {

    public static InvestigationOutcome fromReviewDecision(final String reviewDecision) {
        if (reviewDecision == null) {
            return null;
        }
        return switch (reviewDecision) {
            case "APPROVED" -> new InvestigationOutcome("sar-filed");
            case "REJECTED" -> new InvestigationOutcome("gate-rejected");
            case "UNKNOWN" -> new InvestigationOutcome("decision-not-recorded");
            default -> throw new IllegalStateException(
                    "Unexpected reviewDecision: " + reviewDecision);
        };
    }
}
