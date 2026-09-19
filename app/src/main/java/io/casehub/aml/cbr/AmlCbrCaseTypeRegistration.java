package io.casehub.aml.cbr;

import io.casehub.api.model.cbr.CbrCaseTypeRegistration;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AmlCbrCaseTypeRegistration implements CbrCaseTypeRegistration {

    @Override
    public String cbrType() {
        return PlanCbrCase.CBR_TYPE;
    }

    @Override
    public Class<?> caseClass() {
        return PlanCbrCase.class;
    }
}
