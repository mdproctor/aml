package io.casehub.aml;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.casehub.aml.agents.AgentBehaviour;
import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.InvestigationSummary;
import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SpecialistOutcome;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.channel.Channel;
import io.casehub.qhorus.runtime.channel.ChannelCreateRequest;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.message.MessageService;

/**
 * Layer 3+4: replaces DefaultAmlInvestigationService.
 *
 * <p>Layer 3: dispatches typed COMMANDs to specialist agents via casehub-qhorus channels.
 * Each COMMAND creates a formal Commitment. DECLINE from OSINT is a formal scope boundary.
 *
 * <p>Layer 4: each dispatch carries subjectId=caseId, grouping all message-level ledger
 * entries (MessageLedgerEntry) under the same investigation subject as the AML domain
 * entries (AmlInvestigationLedgerEntry). A regulator querying by caseId sees the full chain.
 */
@ApplicationScoped
public class QhorusAmlInvestigator implements AmlInvestigator {

    private static final String ORCHESTRATOR = "aml-orchestrator";

    private final DefaultSarDraftingService sarDraftingService = new DefaultSarDraftingService();

    @Inject
    MessageService messageService;

    @Inject
    ChannelService channelService;

    @Inject
    Instance<AgentBehaviour> agentBehaviours;

    @Override
    public InvestigationSummary investigate(final SuspiciousTransaction transaction, final UUID caseId) {
        // LAYER 3+4: typed COMMAND/RESPONSE/DONE/DECLINE per specialist agent.
        // caseId is passed as subjectId so all message-level ledger entries are
        // grouped with the AML domain entries under the same investigation subject.
        final SpecialistOutcome<EntityResolutionResult> entity  = dispatch("entity-resolution",  transaction, caseId);
        final SpecialistOutcome<PatternAnalysisResult>  pattern = dispatch("pattern-analysis",   transaction, caseId);
        final SpecialistOutcome<OsintResult>            osint   = dispatch("osint-screening",    transaction, caseId);

        final String sarNarrative = sarDraftingService.draft(transaction, entity, pattern, osint);

        return new InvestigationSummary(transaction, entity, pattern, osint, sarNarrative);
    }

    @SuppressWarnings("unchecked")
    private <T> SpecialistOutcome<T> dispatch(final String capability,
            final SuspiciousTransaction transaction, final UUID caseId) {
        final Channel channel = channelService.findByName(capability)
                .orElseGet(() -> channelService.create(ChannelCreateRequest.builder(capability)
                        .description(ORCHESTRATOR).semantic(ChannelSemantic.APPEND)
                        .adminInstances(ORCHESTRATOR).build()));

        final String correlationId = UUID.randomUUID().toString();

        // Send COMMAND — creates formal obligation record in qhorus.
        // subjectId=caseId links this message-level entry to the AML domain ledger chain.
        final var commandResult = messageService.dispatch(MessageDispatch.builder()
                .channelId(channel.id)
                .sender(ORCHESTRATOR)
                .type(MessageType.COMMAND)
                .content(transaction.id())
                .correlationId(correlationId)
                .subjectId(caseId)
                .actorType(ActorType.SYSTEM)
                .build());

        // Dispatch to registered agent behaviour (in-process for tutorial stubs)
        final AgentBehaviour behaviour = findBehaviour(capability);
        final SpecialistOutcome<?> outcome = behaviour.handle(null);

        // Send DONE/DECLINE/FAILURE — fulfils or declines the commitment.
        // inReplyTo links this entry causally to the COMMAND in the ledger chain.
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

        messageService.dispatch(MessageDispatch.builder()
                .channelId(channel.id)
                .sender(capability)
                .type(replyType)
                .content(content)
                .correlationId(correlationId)
                .inReplyTo(commandResult.messageId())
                .subjectId(caseId)
                .actorType(ActorType.AGENT)
                .build());

        return (SpecialistOutcome<T>) outcome;
    }

    private AgentBehaviour findBehaviour(final String capability) {
        for (final AgentBehaviour b : agentBehaviours) {
            if (capability.equals(b.capability())) {
                return b;
            }
        }
        throw new RuntimeException("No AgentBehaviour registered for capability: " + capability);
    }
}
