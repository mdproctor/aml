package io.casehub.aml.tutorial;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.casehub.aml.AmlInvestigator;
import io.casehub.aml.agents.AgentBehaviour;
import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.InvestigationSummary;
import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SpecialistOutcome;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.ledger.api.model.ActorType;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.channel.Channel;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.message.MessageService;

/**
 * Layer 3: replaces NaiveAmlInvestigator — dispatches typed COMMANDs to specialist
 * agents via casehub-qhorus channels. Each COMMAND creates a formal Commitment.
 * DECLINE from OSINT is a formal scope boundary, not an error.
 *
 * Dispatch approach: direct in-process (calls AgentBehaviour.handle() after sending
 * the COMMAND message). The COMMAND and DONE/DECLINE messages are persisted in qhorus —
 * the formal commitment lifecycle and audit trail exist. The ChannelBackend fan-out
 * pattern (PushAgentDispatch) is the documented production extension for out-of-process
 * agents (claudony workers, Layer 5+).
 */
@ApplicationScoped
public class QhorusAmlInvestigator implements AmlInvestigator {

    private static final String ORCHESTRATOR = "aml-orchestrator";

    private final NaiveSarDraftingService sarDraftingService = new NaiveSarDraftingService();

    @Inject
    MessageService messageService;

    @Inject
    ChannelService channelService;

    @Inject
    Instance<AgentBehaviour> agentBehaviours;

    @Override
    public InvestigationSummary investigate(SuspiciousTransaction transaction) {
        // LAYER 3: typed COMMAND/RESPONSE/DONE/DECLINE per specialist agent.
        // Each dispatch creates a formal Commitment tracked by qhorus.
        SpecialistOutcome<EntityResolutionResult> entity  = dispatch("entity-resolution",  transaction);
        SpecialistOutcome<PatternAnalysisResult>  pattern = dispatch("pattern-analysis",   transaction);
        SpecialistOutcome<OsintResult>            osint   = dispatch("osint-screening",    transaction);

        String sarNarrative = sarDraftingService.draft(transaction, entity, pattern, osint);

        return new InvestigationSummary(transaction, entity, pattern, osint, sarNarrative);
    }

    @SuppressWarnings("unchecked")
    private <T> SpecialistOutcome<T> dispatch(String capability, SuspiciousTransaction transaction) {
        Channel channel = channelService.findByName(capability)
                .orElseGet(() -> channelService.create(
                        capability, ORCHESTRATOR, ChannelSemantic.APPEND, ORCHESTRATOR));

        String correlationId = UUID.randomUUID().toString();

        // Send COMMAND — creates formal obligation record in qhorus
        messageService.send(channel.id, ORCHESTRATOR, MessageType.COMMAND,
                transaction.id(), correlationId, null, null, null, ActorType.SYSTEM);

        // Dispatch to registered agent behaviour (in-process for tutorial stubs)
        AgentBehaviour behaviour = findBehaviour(capability);
        SpecialistOutcome<?> outcome = behaviour.handle(null);

        // Send DONE/DECLINE/FAILURE — fulfils or declines the commitment
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
        messageService.send(channel.id, capability, replyType,
                content, correlationId, null, null, null, ActorType.AGENT);

        return (SpecialistOutcome<T>) outcome;
    }

    private AgentBehaviour findBehaviour(String capability) {
        for (AgentBehaviour b : agentBehaviours) {
            if (capability.equals(b.capability())) {
                return b;
            }
        }
        throw new RuntimeException("No AgentBehaviour registered for capability: " + capability);
    }
}
