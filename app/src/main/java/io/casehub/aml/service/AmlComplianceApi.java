package io.casehub.aml.service;

import io.casehub.aml.compliance.AmlComplianceEvidenceService;
import io.casehub.aml.compliance.AmlErasureService;
import io.casehub.aml.compliance.ActorErasureResult;
import io.casehub.aml.compliance.CrossTenantErasureRequest;
import io.casehub.aml.compliance.CrossTenantErasureResult;
import io.casehub.aml.compliance.EntityErasureResult;
import io.casehub.ledger.api.model.ErasureReason;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.UUID;

@McpDomain(value = "aml/compliance", basePath = "/api")
@ApplicationScoped
public class AmlComplianceApi {

    @Inject AmlComplianceEvidenceService evidenceService;
    @Inject AmlErasureService erasureService;

    @PlatformQuery("Get compliance evidence for an investigation")
    @RestPath("/investigations/{caseId}/compliance-evidence")
    public Object getComplianceEvidence(@PathParam UUID caseId) {
        return evidenceService.findEvidence(caseId)
                .orElseThrow(() -> new NotFoundException("No compliance evidence for case: " + caseId));
    }

    @PlatformMutation("Erase an actor's personal data (GDPR Art.17)")
    @RestPath("/actors/{actorId}/erasure")
    @RolesAllowed("aml-senior-compliance")
    public ActorErasureResult eraseActor(@PathParam String actorId) {
        return erasureService.erase(actorId, ErasureReason.GDPR_ART_17_REQUEST);
    }

    @PlatformMutation("Erase an entity's personal data (GDPR Art.17)")
    @RestPath("/entities/{entityId}/erasure")
    @RolesAllowed("aml-senior-compliance")
    public EntityErasureResult eraseEntity(@PathParam String entityId, String tenantId) {
        if (tenantId != null) {
            return erasureService.eraseEntity(entityId, tenantId, ErasureReason.GDPR_ART_17_REQUEST);
        }
        return erasureService.eraseEntity(entityId, ErasureReason.GDPR_ART_17_REQUEST);
    }

    @PlatformMutation("Erase an entity's data across multiple tenants")
    @RestPath("/entities/{entityId}/erasure/cross-tenant")
    @RolesAllowed("aml-senior-compliance")
    public CrossTenantErasureResult eraseEntityAcrossTenants(
            @PathParam String entityId, CrossTenantErasureRequest request) {
        return erasureService.eraseEntityAcrossTenants(
                entityId, request.tenantIds(), ErasureReason.GDPR_ART_17_REQUEST);
    }
}
