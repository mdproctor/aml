package io.casehub.aml.engine;

import io.casehub.api.model.CaseStatus;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.CaseInstanceRepository;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.platform.api.identity.TenancyConstants;
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
 * <p>Completion is determined by the engine's {@link CaseStatus#COMPLETED} flag. The cache is
 * checked first; if the cache has evicted the entry, the endpoint falls back to
 * {@link CaseInstanceRepository} so that completed cases always return {@code "completed"}
 * regardless of cache lifetime or JVM restarts.
 */
@Path("/api/layer9/investigations")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AmlLayer9Resource {

    @Inject AmlOversightCoordinator coordinator;
    @Inject CaseInstanceCache caseInstanceCache;
    @Inject CaseInstanceRepository caseInstanceRepository;

    @POST
    public Response startInvestigation(final SuspiciousTransaction transaction) {
        final UUID caseId = coordinator.startInvestigation(transaction);
        return Response.accepted(Map.of("caseId", caseId)).build();
    }

    @GET
    @Path("/{caseId}")
    public Response getInvestigation(@PathParam("caseId") final UUID caseId) {
        CaseInstance instance = caseInstanceCache.get(caseId);
        if (instance == null) {
            instance = caseInstanceRepository
                    .findByUuid(caseId, TenancyConstants.DEFAULT_TENANT_ID)
                    .await().indefinitely();
        }
        final boolean completed = instance != null && instance.getState() == CaseStatus.COMPLETED;
        return Response.ok(Map.of(
            "caseId", caseId,
            "status", completed ? "completed" : "in-progress"
        )).build();
    }
}
