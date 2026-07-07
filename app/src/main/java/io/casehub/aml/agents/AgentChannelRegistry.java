package io.casehub.aml.agents;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;

/**
 * Initialises qhorus channels for all registered AgentBehaviour beans and
 * wires each with its PushAgentDispatch backend.
 */
@ApplicationScoped
@Startup
public class AgentChannelRegistry {

    private static final String ORCHESTRATOR = "aml-orchestrator";

    @Inject
    ChannelService channelService;

    @Inject
    ChannelGateway channelGateway;

    @Inject
    Instance<AgentBehaviour> behaviours;

    @Inject
    Instance<AgentDispatchMechanism> dispatchInstances;

    @PostConstruct
    void init() {
        for (AgentBehaviour behaviour : behaviours) {
            AgentDispatchMechanism dispatch = dispatchInstances.get();
            if (!(dispatch instanceof PushAgentDispatch push)) {
                continue; // NoOpAgentDispatch active — no channel wiring needed
            }

            Channel channel = channelService.findByName(behaviour.capability())
                    .orElseGet(() -> channelService.create(ChannelCreateRequest.builder(behaviour.capability())
                            .description(ORCHESTRATOR).semantic(ChannelSemantic.APPEND)
                            .adminInstances(java.util.List.of(ORCHESTRATOR)).build()));

            ChannelRef ref = new ChannelRef(channel.id(), channel.name());
            // Per protocol: open() before registerBackend()
            push.setChannelRef(ref);
            push.open(ref, java.util.Map.of());
            channelGateway.registerBackend(channel.id(), push, push.backendId());
        }
    }

    public ChannelRef channelFor(String capability) {
        Channel channel = channelService.findByName(capability)
                .orElseThrow(() -> new IllegalStateException("No channel for capability: " + capability));
        return new ChannelRef(channel.id(), channel.name());
    }
}
