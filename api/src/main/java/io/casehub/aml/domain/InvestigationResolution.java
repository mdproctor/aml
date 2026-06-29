package io.casehub.aml.domain;

public record InvestigationResolution(
        InvestigationStatus status,
        InvestigationOutcome outcome,
        FailureContext failureContext) {}
