package io.casehub.aml.agents;

import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;

import io.casehub.aml.domain.SpecialistOutcome;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.gateway.AgentChannelBackend;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import io.casehub.qhorus.runtime.message.MessageService;

/**
 * In-process push dispatch — registers as AgentChannelBackend so qhorus fan-out
 * calls post() immediately when a COMMAND arrives. Processes via AgentBehaviour
 * and sends the DONE/DECLINE/FAILURE reply synchronously within the same call.
 * Displaces NoOpAgentDispatch when present.
 *
 * <p>Note: reply-type messages (DONE/DECLINE/FAILURE) require inReplyTo — the Long
 * message ID of the originating COMMAND. This is resolved by looking up the COMMAND
 * message via correlationId. See qhorus#190 for adding inReplyTo to OutboundMessage.
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
    public void start(final AgentBehaviour behaviour) {
        this.behaviour = behaviour;
    }

    public void setChannelRef(final ChannelRef ref) {
        this.channelRef = ref;
    }

    @Override
    public void stop() {
        if (channelRef != null) {
            this.close(channelRef);
        }
    }

    @Override
    public String backendId() {
        return BACKEND_ID_PREFIX + (behaviour != null ? behaviour.capability() : "unbound");
    }

    @Override
    public ActorType actorType() {
        return ActorType.AGENT;
    }

    @Override
    public void open(final ChannelRef ref, final Map<String, String> metadata) {}

    @Override
    public void post(final ChannelRef ref, final OutboundMessage message) {
        if (message.type() != MessageType.COMMAND || behaviour == null) {
            return;
        }

        final String corrId = message.correlationId() != null ? message.correlationId().toString() : null;

        // Resolve the COMMAND message entity for inReplyTo and behaviour context.
        // The COMMAND is already flushed to the qhorus datasource within the current transaction.
        // See qhorus#190 for adding inReplyTo directly to OutboundMessage.
        final io.casehub.qhorus.api.message.Message commandMessage = corrId != null
                ? messageService.findAllByCorrelationId(corrId).stream()
                        .filter(m -> m.messageType() == MessageType.COMMAND)
                        .findFirst()
                        .orElse(null)
                : null;

        final SpecialistOutcome<?> outcome = behaviour.handle(commandMessage);

        final MessageType replyType = switch (outcome) {
            case SpecialistOutcome.Completed<?> ignored -> MessageType.DONE;
            case SpecialistOutcome.Declined<?> ignored  -> MessageType.DECLINE;
            case SpecialistOutcome.Failed<?> ignored    -> MessageType.FAILURE;
        };

        final String content = switch (outcome) {
            case SpecialistOutcome.Completed<?> ignored -> null;
            case SpecialistOutcome.Declined<?> d        -> d.reason();
            case SpecialistOutcome.Failed<?> f          -> f.reason();
        };

        final Long commandId = commandMessage != null ? commandMessage.id() : null;

        // subjectId not propagated here: OutboundMessage does not carry subjectId (qhorus#190).
        // The tutorial uses QhorusAmlInvestigator for ledger-linked dispatches; PushAgentDispatch
        // is an alternative backend path. When qhorus#190 ships and OutboundMessage gains subjectId,
        // add: .subjectId(message.subjectId()) to restore the full caseId chain.
        messageService.dispatch(MessageDispatch.builder()
                .channelId(ref.id())
                .sender(behaviour.capability())
                .type(replyType)
                .content(content)
                .correlationId(corrId)
                .inReplyTo(commandId)
                .actorType(ActorType.AGENT)
                .build());
    }

    @Override
    public void close(final ChannelRef ref) {}
}
