package io.casehub.aml.triage;

import io.casehub.aml.domain.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HardGateEvaluatorTest {

    private final HardGateEvaluator evaluator = new HardGateEvaluator();

    private TriageInput input(String entityType, double riskScore,
                              boolean sanctionsHit, boolean pepHit, boolean declined) {
        return new TriageInput(
                new EntityResolutionResult("E-1", "chain", entityType, riskScore),
                new PatternAnalysisResult(false, "none"),
                new OsintResult(sanctionsHit, pepHit, declined, "reason"),
                null);
    }

    @Test
    void sanctionsHit_returnsSarWarranted() {
        var result = evaluator.evaluate(input("CORPORATE", 0.2, true, false, false));
        assertTrue(result.isPresent());
        assertEquals(TriageDecision.SAR_WARRANTED, result.get().decision());
        assertEquals(HardGate.SANCTIONS_HIT, result.get().hardGate());
        assertEquals(1.0, result.get().riskScore());
    }

    @Test
    void confirmedPep_returnsSarWarranted() {
        var result = evaluator.evaluate(input("PEP", 0.5, false, true, false));
        assertTrue(result.isPresent());
        assertEquals(TriageDecision.SAR_WARRANTED, result.get().decision());
        assertEquals(HardGate.CONFIRMED_PEP, result.get().hardGate());
    }

    @Test
    void pepHit_butNotPepEntityType_noGate() {
        var result = evaluator.evaluate(input("CORPORATE", 0.5, false, true, false));
        assertTrue(result.isEmpty());
    }

    @Test
    void shellCompany_returnsSarWarranted() {
        var result = evaluator.evaluate(input("SHELL_COMPANY", 0.3, false, false, false));
        assertTrue(result.isPresent());
        assertEquals(TriageDecision.SAR_WARRANTED, result.get().decision());
        assertEquals(HardGate.SHELL_COMPANY, result.get().hardGate());
    }

    @Test
    void sanctionsHit_takesPriorityOverPep() {
        var result = evaluator.evaluate(input("PEP", 0.9, true, true, false));
        assertTrue(result.isPresent());
        assertEquals(HardGate.SANCTIONS_HIT, result.get().hardGate());
    }

    @Test
    void lowRiskIndividual_cleanOsint_noGate() {
        var result = evaluator.evaluate(input("INDIVIDUAL", 0.1, false, false, false));
        assertTrue(result.isEmpty());
    }

    @Test
    void declinedOsint_noGate() {
        var result = evaluator.evaluate(input("CORPORATE", 0.5, false, false, true));
        assertTrue(result.isEmpty());
    }

    @Test
    void gateResult_hasEmptyFactorsList() {
        var result = evaluator.evaluate(input("SHELL_COMPANY", 0.3, false, false, false));
        assertTrue(result.get().factors().isEmpty());
        assertNull(result.get().cbrThresholdAdjustment());
    }
}
