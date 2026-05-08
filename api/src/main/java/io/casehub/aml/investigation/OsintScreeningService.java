package io.casehub.aml.investigation;

import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.SuspiciousTransaction;

public interface OsintScreeningService {
    OsintResult screen(SuspiciousTransaction transaction);
}
