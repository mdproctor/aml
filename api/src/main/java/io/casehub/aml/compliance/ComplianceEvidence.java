package io.casehub.aml.compliance;

import java.time.Instant;
import java.util.UUID;

public record ComplianceEvidence(
    UUID caseId,
    Instant generatedAt,
    AuditChainRequirement auditChain,
    SlaRequirement sla,
    TrustRoutingRequirement trustRouting,
    GdprErasureRequirement gdprErasure,
    String signature
) {}
