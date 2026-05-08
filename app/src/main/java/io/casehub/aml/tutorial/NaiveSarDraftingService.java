package io.casehub.aml.tutorial;

import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.investigation.SarDraftingService;

class NaiveSarDraftingService implements SarDraftingService {

    @Override
    public String draft(SuspiciousTransaction transaction,
                        EntityResolutionResult entity,
                        PatternAnalysisResult pattern,
                        OsintResult osint) {
        return "";
    }
}
