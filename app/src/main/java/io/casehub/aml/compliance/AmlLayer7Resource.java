package io.casehub.aml.compliance;

import io.casehub.ledger.runtime.privacy.LedgerErasureService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

/**
 * Layer 7: REST endpoints for compliance evidence retrieval and GDPR erasure.
 *
 * <p>Exposes two capabilities:
 * <ul>
 *   <li>GET compliance evidence for a completed investigation -- maps FinCEN/FATF
 *       requirements to their current status (audit chain, SLA, trust routing, GDPR).</li>
 *   <li>POST actor erasure -- pseudonymizes an actor's identity in ledger entries,
 *       preserving audit structure while satisfying GDPR Art. 17.</li>
 * </ul>
 */
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AmlLayer7Resource {

    @Inject
    AmlComplianceEvidenceService evidenceService;

    @Inject
    LedgerErasureService erasureService;

    @GET
    @Path("/api/investigations/{caseId}/compliance-evidence")
    public Response getComplianceEvidence(@PathParam("caseId") UUID caseId) {
        return evidenceService.findEvidence(caseId)
            .map(e -> Response.ok(e).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Path("/api/actors/{actorId}/erasure")
    public LedgerErasureService.ErasureResult eraseActor(
            @PathParam("actorId") String actorId) {
        return erasureService.erase(actorId);
    }
}
