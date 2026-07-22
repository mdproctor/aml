package io.casehub.aml.domain;

public record EntityResolutionResult(
        String entityId,
        String ownershipChain,
        String entityType,
        double riskScore) {
    public EntityResolutionResult {
        if (riskScore < 0.0 || riskScore > 1.0) {
            throw new IllegalArgumentException(
                    "riskScore must be in [0.0, 1.0], got: " + riskScore);
        }
    }
}
