package io.casehub.aml.domain;

public record InvestigationSummary(
        SuspiciousTransaction transaction,
        EntityResolutionResult entityResolution,
        PatternAnalysisResult patternAnalysis,
        OsintResult osintScreening,
        String sarNarrative) {}
