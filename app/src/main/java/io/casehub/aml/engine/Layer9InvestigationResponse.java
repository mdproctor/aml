package io.casehub.aml.engine;

import io.casehub.aml.domain.FailureContext;
import io.casehub.aml.domain.InvestigationOutcome;
import io.casehub.aml.domain.InvestigationStatus;
import java.util.UUID;

public record Layer9InvestigationResponse(
        UUID caseId,
        InvestigationStatus status,
        InvestigationOutcome outcome,
        FailureContext failureContext) {}
