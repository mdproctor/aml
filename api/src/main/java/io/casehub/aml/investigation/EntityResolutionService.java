package io.casehub.aml.investigation;

import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.SuspiciousTransaction;

public interface EntityResolutionService {
    EntityResolutionResult resolve(SuspiciousTransaction transaction);
}
