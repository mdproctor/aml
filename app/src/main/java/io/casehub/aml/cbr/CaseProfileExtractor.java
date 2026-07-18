package io.casehub.aml.cbr;

import io.casehub.aml.domain.CaseProfile;
import io.casehub.aml.domain.EntityType;
import io.casehub.aml.domain.JurisdictionRisk;
import io.casehub.aml.domain.NetworkComplexity;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.memory.AmlPriorContext;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CaseProfileExtractor {

    public CaseProfile extractInitial(SuspiciousTransaction tx, AmlPriorContext priorContext) {
        return CaseProfile.initial(
                tx.flagReason(),
                tx.amount(),
                priorContext.entityRisk().size());
    }

    public CaseProfile extractComplete(SuspiciousTransaction tx, AmlPriorContext priorContext,
                                       EntityType entityType, JurisdictionRisk jurisdiction,
                                       NetworkComplexity network) {
        return CaseProfile.complete(
                tx.flagReason(),
                tx.amount(),
                priorContext.entityRisk().size(),
                entityType, jurisdiction, network);
    }
}
