package io.casehub.aml.agents;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.Dependent;

/**
 * No-op dispatch — satisfies CDI wiring before PushAgentDispatch is implemented.
 * PushAgentDispatch (@Dependent, no @DefaultBean) will displace this when wired.
 */
@Dependent
@DefaultBean
public class NoOpAgentDispatch implements AgentDispatchMechanism {

    @Override
    public void start(AgentBehaviour behaviour) {
        // no-op — agents not yet connected to qhorus channels
    }

    @Override
    public void stop() {
        // no-op
    }
}
