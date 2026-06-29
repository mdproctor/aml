package io.casehub.aml.engine;

import io.casehub.aml.domain.FailureContext;
import io.casehub.aml.domain.InvestigationOutcome;
import io.casehub.aml.domain.InvestigationStatus;
import java.util.List;
import java.util.UUID;

public record Layer6InvestigationResponse(
        UUID caseId,
        InvestigationStatus status,
        List<WorkerRoutingDecision> routingDecisions,
        InvestigationOutcome outcome,
        FailureContext failureContext) {}
