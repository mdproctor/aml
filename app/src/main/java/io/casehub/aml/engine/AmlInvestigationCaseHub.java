package io.casehub.aml.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.engine.YamlCaseHub;
import io.casehub.api.model.CaseDefinition;
import io.casehub.aml.ComplianceReviewLifecycle;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AmlInvestigationCaseHub extends YamlCaseHub {

    private static final Logger LOG = Logger.getLogger(AmlInvestigationCaseHub.class);

    @Inject ComplianceReviewLifecycle complianceReviewLifecycle;
    @Inject ObjectMapper objectMapper;

    public AmlInvestigationCaseHub() {
        super("aml/aml-investigation.yaml");
    }

    @Override
    protected void augment(CaseDefinition definition) {
        final var descriptor = new AmlInvestigationCaseDescriptor(complianceReviewLifecycle, objectMapper);
        definition.getWorkers().addAll(descriptor.workers());
        LOG.debugf("AML investigation definition augmented: %d workers registered",
                definition.getWorkers().size());
    }
}
