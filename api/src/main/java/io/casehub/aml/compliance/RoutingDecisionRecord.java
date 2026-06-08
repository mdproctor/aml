package io.casehub.aml.compliance;

import java.util.UUID;

public record RoutingDecisionRecord(
    String capabilityTag,
    String selectedWorker,
    Double trustScoreAtRouting,
    double thresholdApplied,
    UUID attestationEntryId,
    boolean reconstructed,
    boolean observerFailed
) {}
