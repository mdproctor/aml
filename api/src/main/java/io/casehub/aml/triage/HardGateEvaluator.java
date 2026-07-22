package io.casehub.aml.triage;

import io.casehub.aml.domain.HardGate;
import io.casehub.aml.domain.RiskFactor;
import io.casehub.aml.domain.TriageDecision;
import io.casehub.aml.domain.TriageInput;
import io.casehub.aml.domain.TriageResult;

import java.util.List;
import java.util.Optional;

public final class HardGateEvaluator {

    public Optional<TriageResult> evaluate(TriageInput input) {
        var osint = input.osintScreening();
        var entityType = input.entityResolution().entityType();

        if (osint.sanctionsHit()) {
            return gate(HardGate.SANCTIONS_HIT, "Hard gate: sanctions hit on OFAC/SDN screening");
        }
        if (osint.pepHit() && "PEP".equals(entityType)) {
            return gate(HardGate.CONFIRMED_PEP, "Hard gate: confirmed PEP with OSINT PEP hit");
        }
        if ("SHELL_COMPANY".equals(entityType)) {
            return gate(HardGate.SHELL_COMPANY, "Hard gate: shell company entity type");
        }
        return Optional.empty();
    }

    private Optional<TriageResult> gate(HardGate gate, String reason) {
        return Optional.of(new TriageResult(
                TriageDecision.SAR_WARRANTED, reason, 1.0, gate, null, List.of()));
    }
}
