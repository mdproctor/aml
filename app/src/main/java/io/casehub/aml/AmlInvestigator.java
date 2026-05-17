package io.casehub.aml;

import io.casehub.aml.domain.InvestigationSummary;
import io.casehub.aml.domain.SuspiciousTransaction;

public interface AmlInvestigator {
    InvestigationSummary investigate(SuspiciousTransaction transaction);
}
