package io.casehub.aml.compliance;

import java.util.List;

public record TrustRoutingRequirement(
    String id,
    String citation,
    String mechanism,
    RequirementStatus status,
    List<RoutingDecisionRecord> decisions
) {
    public static final String REQUIREMENT_ID = "FATF-R20-TRUST-ROUTING";
    public static final String CITATION =
        "FATF Recommendation 20 — Experienced analysts on complex cases";
    public static final String MECHANISM =
        "Layer 6: TrustWeightedAgentStrategy reads AmlTrustRoutingPolicyProvider thresholds. " +
        "Layer 7: AmlTrustRoutingAttestation captures trustScoreAtRouting at WorkerDecisionEvent " +
        "time before TrustScoreCache can drift. Workaround for casehubio/engine#403.";
}
