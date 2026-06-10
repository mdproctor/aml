package io.casehub.aml.engine;

import io.casehub.api.model.CaseStatus;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;

/**
 * Layer 9 REST resource — oversight gate investigation.
 *
 * <p>Completion is determined by the engine's {@link CaseStatus#COMPLETED} flag on the
 * {@link CaseInstanceCache}, which is set once all bindings and goals are satisfied.
 * This is more reliable than querying {@code WorkerDecisionEntry} (which requires tenancyId).
 */
@Path("/api/layer9/investigations")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AmlLayer9Resource {

    @Inject AmlOversightCoordinator coordinator;
    @Inject CaseInstanceCache caseInstanceCache;

    @POST
    public Response startInvestigation(final SuspiciousTransaction transaction) {
        final UUID caseId = coordinator.startInvestigation(transaction);
        return Response.accepted(Map.of("caseId", caseId)).build();
    }

    @GET
    @Path("/{caseId}")
    public Response getInvestigation(@PathParam("caseId") final UUID caseId) {
        final var instance = caseInstanceCache.get(caseId);
        final boolean completed = instance != null && instance.getState() == CaseStatus.COMPLETED;
        return Response.ok(Map.of(
            "caseId", caseId,
            "status", completed ? "completed" : "in-progress"
        )).build();
    }
}
