package io.casehub.aml.engine;

import java.util.List;
import java.util.UUID;

public record Layer6InvestigationResponse(
        UUID caseId,
        String status,                          // "completed" | "in-progress"
        List<WorkerRoutingDecision> routingDecisions) {}
