package io.casehub.aml.triage;

import io.casehub.aml.domain.TriageDecision;
import io.casehub.aml.domain.TriageInput;
import io.casehub.aml.domain.TriageResult;

public final class InvestigationTriageEvaluator {

    private final HardGateEvaluator hardGateEvaluator;
    private final RiskScorer riskScorer;
    private final CbrAdjuster cbrAdjuster;
    private final double sarThreshold;
    private final double fpThreshold;

    public InvestigationTriageEvaluator(double sarThreshold, double fpThreshold,
                                        double maxCbrAdjustment, double cbrMinConfidence) {
        this.hardGateEvaluator = new HardGateEvaluator();
        this.riskScorer = new RiskScorer();
        this.cbrAdjuster = new CbrAdjuster(maxCbrAdjustment, cbrMinConfidence);
        this.sarThreshold = sarThreshold;
        this.fpThreshold = fpThreshold;
    }

    public TriageResult evaluate(TriageInput input) {
        var gateResult = hardGateEvaluator.evaluate(input);
        if (gateResult.isPresent()) {
            return gateResult.get();
        }

        var scoring = riskScorer.score(input);
        var adjusted = cbrAdjuster.adjust(sarThreshold, fpThreshold, input.cbrPathAdvice());

        TriageDecision decision;
        String reason;
        if (scoring.score() >= adjusted.sarThreshold()) {
            decision = TriageDecision.SAR_WARRANTED;
            reason = String.format("Risk score %.3f >= SAR threshold %.3f", scoring.score(), adjusted.sarThreshold());
        } else if (scoring.score() <= adjusted.fpThreshold()) {
            decision = TriageDecision.FALSE_POSITIVE;
            reason = String.format("Risk score %.3f <= FP threshold %.3f", scoring.score(), adjusted.fpThreshold());
        } else {
            decision = TriageDecision.INCONCLUSIVE;
            reason = String.format("Risk score %.3f in ambiguous band (%.3f, %.3f)",
                    scoring.score(), adjusted.fpThreshold(), adjusted.sarThreshold());
        }

        return new TriageResult(decision, reason, scoring.score(),
                null, adjusted.adjustment(), scoring.factors());
    }
}
