package io.casehub.aml;

import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.investigation.OsintScreeningService;

public class DefaultOsintScreeningService implements OsintScreeningService {

    @Override
    public OsintResult screen(SuspiciousTransaction transaction) {
        return new OsintResult(false, false, false, "");
    }
}
