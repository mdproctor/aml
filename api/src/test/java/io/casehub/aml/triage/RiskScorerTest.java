package io.casehub.aml.triage;

import io.casehub.aml.domain.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiskScorerTest {

    private final RiskScorer scorer = new RiskScorer();

    private TriageInput input(String entityType, double riskScore,
                              boolean structuring, boolean pepHit,
                              boolean sanctionsHit, boolean declined) {
        return new TriageInput(
                new EntityResolutionResult("E-1", "chain", entityType, riskScore),
                new PatternAnalysisResult(structuring, "desc"),
                new OsintResult(sanctionsHit, pepHit, declined, "reason"),
                null);
    }

    @Test
    void highRisk_structuring_pep_yieldsHighScore() {
        var result = scorer.score(input("PEP", 0.9, true, false, false, false));
        // 0.9*0.35 + 1.0*0.25 + 1.0*0.20 + 0.0*0.10 + 0.0*0.10 = 0.865
        assertEquals(0.765, result.score(), 0.001);
    }

    @Test
    void lowRisk_noFlags_yieldsLowScore() {
        var result = scorer.score(input("INDIVIDUAL", 0.1, false, false, false, false));
        // 0.1*0.35 = 0.035
        assertEquals(0.035, result.score(), 0.001);
    }

    @Test
    void osintDeclined_contributesPartialScore() {
        var result = scorer.score(input("INDIVIDUAL", 0.1, false, false, false, true));
        // 0.1*0.35 + 0.5*0.10 = 0.035 + 0.05 = 0.085
        assertEquals(0.085, result.score(), 0.001);
    }

    @Test
    void scoreAlwaysInUnitRange() {
        var maxInput = input("PEP", 1.0, true, false, false, false);
        assertTrue(scorer.score(maxInput).score() <= 1.0);

        var minInput = input("INDIVIDUAL", 0.0, false, false, false, false);
        assertTrue(scorer.score(minInput).score() >= 0.0);
    }

    @Test
    void factorsListContainsContributingSignals() {
        var result = scorer.score(input("PEP", 0.5, true, false, false, false));
        assertTrue(result.factors().size() >= 3);
        var names = result.factors().stream().map(RiskFactor::name).toList();
        assertTrue(names.contains("entity-risk-score"));
        assertTrue(names.contains("structuring-detected"));
        assertTrue(names.contains("pep-entity-type"));
    }

    @Test
    void pepHit_osint_contributesWeight() {
        var withPepHit = scorer.score(input("CORPORATE", 0.3, false, true, false, false));
        var withoutPepHit = scorer.score(input("CORPORATE", 0.3, false, false, false, false));
        assertEquals(0.10, withPepHit.score() - withoutPepHit.score(), 0.001);
    }
}
