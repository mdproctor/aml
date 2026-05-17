package io.casehub.aml.agents;

import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;

import io.casehub.aml.domain.SpecialistOutcome;
import io.casehub.ledger.api.model.ActorType;
import io.casehub.qhorus.api.gateway.AgentChannelBackend;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import io.casehub.qhorus.runtime.message.MessageService;

/**
 * In-process push dispatch — registers as AgentChannelBackend so qhorus fan-out
 * calls post() immediately when a COMMAND arrives. Processes via AgentBehaviour
 * and sends the DONE/DECLINE/FAILURE reply synchronously within the same call.
 * Displaces NoOpAgentDispatch when present.
 */
@Dependent
@Typed({AgentDispatchMechanism.class, PushAgentDispatch.class})
public class PushAgentDispatch implements AgentDispatchMechanism, AgentChannelBackend {

    private static final String BACKEND_ID_PREFIX = "aml-push-";

    @Inject
    ChannelGateway channelGateway;

    @Inject
    MessageService messageService;

    private AgentBehaviour behaviour;
    private ChannelRef channelRef;

    @Override
    public void start(AgentBehaviour behaviour) {
        this.behaviour = behaviour;
        // open() and registerBackend() are called by AgentChannelRegistry after setChannelRef()
    }

    public void setChannelRef(ChannelRef ref) {
        this.channelRef = ref;
    }

    @Override
    public void stop() {
        if (channelRef != null) {
            this.close(channelRef);
        }
    }

    // AgentChannelBackend / ChannelBackend methods

    @Override
    public String backendId() {
        return BACKEND_ID_PREFIX + (behaviour != null ? behaviour.capability() : "unbound");
    }

    @Override
    public ActorType actorType() {
        return ActorType.AGENT;
    }

    @Override
    public void open(ChannelRef ref, Map<String, String> metadata) {
        // no-op — backend state initialised in start()
    }

    @Override
    public void post(ChannelRef ref, OutboundMessage message) {
        if (message.type() != MessageType.COMMAND || behaviour == null) {
            return;
        }

        SpecialistOutcome<?> outcome = behaviour.handle(null);

        MessageType replyType = switch (outcome) {
            case SpecialistOutcome.Completed<?> ignored -> MessageType.DONE;
            case SpecialistOutcome.Declined<?> ignored  -> MessageType.DECLINE;
            case SpecialistOutcome.Failed<?> ignored    -> MessageType.FAILURE;
        };

        String content = switch (outcome) {
            case SpecialistOutcome.Completed<?> ignored -> null;
            case SpecialistOutcome.Declined<?> d        -> d.reason();
            case SpecialistOutcome.Failed<?> f          -> f.reason();
        };

        messageService.send(
                ref.id(),
                behaviour.capability(),
                replyType,
                content,
                message.correlationId().toString(),
                null,
                message.sender(),
                null,
                ActorType.AGENT
        );
    }

    @Override
    public void close(ChannelRef ref) {
        // no-op
    }
}
