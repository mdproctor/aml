package io.casehub.aml.agents;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.SpecialistOutcome;
import io.casehub.qhorus.api.message.Message;

@ApplicationScoped
@DefaultBean
public class OsintScreeningBehaviour implements AgentBehaviour {

    private static final String CAPABILITY = "osint-screening";
    private static final String AGENT_ID   = "aml-osint-stub";

    @Override
    public String capability() {
        return CAPABILITY;
    }

    @Override
    public SpecialistOutcome<OsintResult> handle(Message command) {
        return new SpecialistOutcome.Declined<>(
                AGENT_ID, CAPABILITY,
                "insufficient clearance for PEP database access");
    }
}
