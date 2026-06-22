package io.casehub.aml.engine;

import io.casehub.api.model.CaseStatus;
import io.casehub.aml.domain.SarOutcome;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.trust.AmlWorkerDecisionRepository;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.CaseInstanceRepository;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.model.WorkerDecisionEntry;
import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
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
    @Inject CaseInstanceCache caseInstanceCache;
    @Inject CaseInstanceRepository caseInstanceRepository;

    @POST
    public Response startInvestigation(final SuspiciousTransaction transaction) {
        final UUID caseId = coordinator.startInvestigation(transaction);
        return Response.accepted(Map.of("caseId", caseId)).build();
    }

    /**
     * Returns the investigation status and routing decisions for a completed case.
     *
     * <p>Completion is determined by {@link CaseStatus#COMPLETED}. The cache is checked first;
     * if the cache has evicted the entry (TTL expiry or JVM restart), the endpoint falls back
     * to {@link CaseInstanceRepository} so that completed cases always return {@code "completed"}
     * regardless of cache lifetime.
     *
     * <p>The {@code trustScore} in each {@link WorkerRoutingDecision} reflects the score from
     * {@link TrustScoreSource} at response time, not the score used at routing time.
     */
    @GET
    @Path("/{caseId}")
    public Layer6InvestigationResponse getInvestigation(@PathParam("caseId") UUID caseId) {
        CaseInstance instance = caseInstanceCache.get(caseId);
        if (instance == null) {
            instance = caseInstanceRepository
                    .findByUuid(caseId, TenancyConstants.DEFAULT_TENANT_ID)
                    .await().indefinitely();
        }
        final boolean completed = instance != null && instance.getState() == CaseStatus.COMPLETED;

        if (!completed) {
            return new Layer6InvestigationResponse(caseId, "in-progress", List.of());
        }

        final List<WorkerDecisionEntry> entries = workerDecisionRepo.findAllByCaseId(caseId);
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
