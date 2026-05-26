package io.casehub.aml;

import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.investigation.PatternAnalysisService;

public class DefaultPatternAnalysisService implements PatternAnalysisService {

    @Override
    public PatternAnalysisResult analyze(SuspiciousTransaction transaction) {
        return new PatternAnalysisResult(false, "");
    }
}
