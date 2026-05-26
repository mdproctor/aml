package io.casehub.aml;

import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.investigation.EntityResolutionService;

public class DefaultEntityResolutionService implements EntityResolutionService {

    @Override
    public EntityResolutionResult resolve(SuspiciousTransaction transaction) {
        return new EntityResolutionResult("entity-stub", "direct-owner", "CORPORATE", 0.35);
    }
}
