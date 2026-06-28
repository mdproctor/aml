package io.casehub.aml.engine;

import io.casehub.aml.domain.InvestigationOutcome;
import java.util.List;
import java.util.UUID;

public record Layer6InvestigationResponse(
        UUID caseId,
        String status,                          // "completed" | "in-progress"
        List<WorkerRoutingDecision> routingDecisions,
        InvestigationOutcome outcome) {}         // null while in-progress or before officer review
