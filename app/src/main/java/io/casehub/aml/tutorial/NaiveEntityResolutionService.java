package io.casehub.aml.tutorial;

import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.investigation.EntityResolutionService;

public class NaiveEntityResolutionService implements EntityResolutionService {

    @Override
    public EntityResolutionResult resolve(SuspiciousTransaction transaction) {
        return new EntityResolutionResult("", "");
    }
}
