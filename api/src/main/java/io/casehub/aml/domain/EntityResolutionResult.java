package io.casehub.aml.domain;

/**
 * Output of entity resolution for a flagged transaction.
 *
 * <p>Layer 5 adds {@code entityType} and {@code riskScore} to drive adaptive routing:
 * the engine's {@code senior-analyst-required} binding fires when {@code entityType == "PEP"}
 * or {@code riskScore > 0.8}.
 */
public record EntityResolutionResult(
        String entityId,
        String ownershipChain,
        String entityType,
        double riskScore) {}
