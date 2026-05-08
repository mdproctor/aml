package io.casehub.aml.tutorial;

import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.investigation.PatternAnalysisService;

class NaivePatternAnalysisService implements PatternAnalysisService {

    @Override
    public PatternAnalysisResult analyze(SuspiciousTransaction transaction) {
        return new PatternAnalysisResult(false, "");
    }
}
