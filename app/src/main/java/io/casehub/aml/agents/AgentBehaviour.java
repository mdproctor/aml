package io.casehub.aml.agents;

import io.casehub.aml.domain.SpecialistOutcome;
import io.casehub.qhorus.runtime.message.Message;

public interface AgentBehaviour {
    String capability();

    /**
     * Handle an incoming COMMAND message and return the outcome.
     * Contract: during Layer 3 in-process dispatch, {@code command} may be null.
     * Implementations must not assume it is non-null until Layer 5 (real agent wiring).
     */
    SpecialistOutcome<?> handle(Message command);
}
