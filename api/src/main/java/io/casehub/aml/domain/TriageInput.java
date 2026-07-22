package io.casehub.aml.domain;

public record TriageInput(
        EntityResolutionResult entityResolution,
        PatternAnalysisResult patternAnalysis,
        OsintResult osintScreening,
        CbrPathAdvice cbrPathAdvice) {}
