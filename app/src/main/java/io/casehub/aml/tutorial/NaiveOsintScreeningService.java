package io.casehub.aml.tutorial;

import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.investigation.OsintScreeningService;

class NaiveOsintScreeningService implements OsintScreeningService {

    @Override
    public OsintResult screen(SuspiciousTransaction transaction) {
        return new OsintResult(false, false, "");
    }
}
