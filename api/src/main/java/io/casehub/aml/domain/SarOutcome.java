package io.casehub.aml.domain;

public record SarOutcome(
        SarVerdict verdict,
        String reason,
        double investigationAccuracyScore) {}
