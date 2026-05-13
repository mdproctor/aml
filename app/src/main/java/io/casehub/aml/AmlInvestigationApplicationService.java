package io.casehub.aml;

import io.casehub.aml.domain.AmlInvestigationResult;
import io.casehub.aml.domain.SuspiciousTransaction;

public interface AmlInvestigationApplicationService {
    AmlInvestigationResult investigate(SuspiciousTransaction transaction);
}
