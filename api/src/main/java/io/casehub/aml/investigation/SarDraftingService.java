package io.casehub.aml.investigation;

import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SuspiciousTransaction;

public interface SarDraftingService {
    String draft(SuspiciousTransaction transaction,
                 EntityResolutionResult entity,
                 PatternAnalysisResult pattern,
                 OsintResult osint);
}
