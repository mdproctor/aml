package io.casehub.aml.investigation;

import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SuspiciousTransaction;

public interface PatternAnalysisService {
    PatternAnalysisResult analyze(SuspiciousTransaction transaction);
}
