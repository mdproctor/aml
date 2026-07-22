package io.casehub.aml.domain;

import java.util.List;

public record TriageResult(
        TriageDecision decision,
        String reason,
        double riskScore,
        HardGate hardGate,
        Double cbrThresholdAdjustment,
        List<RiskFactor> factors) {}
