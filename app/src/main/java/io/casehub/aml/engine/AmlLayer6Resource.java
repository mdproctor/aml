package io.casehub.aml.engine;

import io.casehub.aml.domain.SarOutcome;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.trust.AmlWorkerDecisionRepository;
import io.casehub.aml.engine.SarOutcomeRecordedEvent;
import jakarta.enterprise.event.Event;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.model.WorkerDecisionEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;

@Path("/api/layer6/investigations")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AmlLayer6Resource {

    @Inject AmlEngineCoordinator coordinator;
    @Inject AmlWorkerDecisionRepository workerDecisionRepo;
    @Inject Event<SarOutcomeRecordedEvent> sarOutcomeEvent;
    @Inject TrustScoreSource trustScoreSource;

    @POST
    public Response startInvestigation(final SuspiciousTransaction transaction) {
        final UUID caseId = coordinator.startInvestigation(transaction);
        return Response.accepted(Map.of("caseId", caseId)).build();
    }

    /**
     * Returns the investigation status and routing decisions for a completed case.
     *
     * <p>The {@code trustScore} in each {@link WorkerRoutingDecision} reflects the score from
     * {@link TrustScoreSource} at response time, not the score used at routing time.
     * After recording a SAR outcome, the score will drift once the next scoring cycle runs.
     * This is intentional for the tutorial — it shows "current trust level" rather than
     * "trust level that drove this routing decision."
     */
    @GET
    @Path("/{caseId}")
    public Layer6InvestigationResponse getInvestigation(@PathParam("caseId") UUID caseId) {
        final List<WorkerDecisionEntry> entries = workerDecisionRepo.findAllByCaseId(caseId);

        final boolean completed = entries.stream()
                .anyMatch(e -> "sar-drafting".equals(e.capabilityTag));

        if (!completed) {
            return new Layer6InvestigationResponse(caseId, "in-progress", List.of());
        }

        final List<WorkerRoutingDecision> decisions = entries.stream()
                .map(e -> {
                    final OptionalDouble score =
                            trustScoreSource.capabilityScore(e.workerId, e.capabilityTag);
                    return new WorkerRoutingDecision(
                            e.capabilityTag,
                            e.workerId,
                            score.isPresent() ? score.getAsDouble() : null);
                })
                .toList();

        return new Layer6InvestigationResponse(caseId, "completed", decisions);
    }

    @POST
    @Path("/{caseId}/outcome")
    public Response recordOutcome(
            @PathParam("caseId") UUID caseId,
            final SarOutcomeRequest request) {
        final SarOutcome outcome = new SarOutcome(
                request.verdict(), request.reason(), request.investigationAccuracyScore());
        sarOutcomeEvent.fire(new SarOutcomeRecordedEvent(caseId, outcome));
        return Response.noContent().build();
    }
}
