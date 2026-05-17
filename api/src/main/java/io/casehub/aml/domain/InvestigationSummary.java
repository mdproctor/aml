package io.casehub.aml.domain;

public record InvestigationSummary(
        SuspiciousTransaction transaction,
        SpecialistOutcome<EntityResolutionResult> entityResolution,
        SpecialistOutcome<PatternAnalysisResult>  patternAnalysis,
        SpecialistOutcome<OsintResult>            osintScreening,
        String sarNarrative) {}
