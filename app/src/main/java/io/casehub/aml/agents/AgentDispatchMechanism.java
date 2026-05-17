package io.casehub.aml.agents;

public interface AgentDispatchMechanism {
    void start(AgentBehaviour behaviour);
    void stop();
}
