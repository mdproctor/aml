package io.casehub.aml.agents;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.SpecialistOutcome;
import io.casehub.aml.DefaultEntityResolutionService;
import io.casehub.qhorus.runtime.message.Message;

@ApplicationScoped
@DefaultBean
public class EntityResolutionBehaviour implements AgentBehaviour {

    private static final String CAPABILITY = "entity-resolution";

    private final DefaultEntityResolutionService service = new DefaultEntityResolutionService();

    @Override
    public String capability() {
        return CAPABILITY;
    }

    @Override
    public SpecialistOutcome<EntityResolutionResult> handle(Message command) {
        return new SpecialistOutcome.Completed<>(service.resolve(null));
    }
}
