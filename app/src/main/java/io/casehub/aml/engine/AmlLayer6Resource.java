package io.casehub.aml.engine;

import io.casehub.aml.domain.SarOutcome;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.trust.AmlWorkerDecisionRepository;
import io.casehub.aml.trust.SarOutcomeFeedbackService;
import io.casehub.ledger.model.WorkerDecisionEntry;
import io.casehub.ledger.routing.TrustScoreCache;
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
    @Inject SarOutcomeFeedbackService feedbackService;
    @Inject TrustScoreCache trustScoreCache;

    @POST
    public Response startInvestigation(final SuspiciousTransaction transaction) {
        final UUID caseId = coordinator.startInvestigation(transaction);
        return Response.accepted(Map.of("caseId", caseId)).build();
    }

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
                            trustScoreCache.getCapabilityScore(e.workerId, e.capabilityTag);
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
            final SarOutcome outcome) {
        feedbackService.recordOutcome(caseId, outcome);
        return Response.noContent().build();
    }
}
