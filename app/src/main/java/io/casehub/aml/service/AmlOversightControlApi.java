package io.casehub.aml.service;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.RestPath;
import io.casehub.platform.api.mcp.RestStatus;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@McpDomain(value = "aml/oversight-control", basePath = "/api")
@ApplicationScoped
public class AmlOversightControlApi {

    @Inject CaseHubRuntime caseHubRuntime;

    @PlatformMutation("Suspend an active investigation")
    @RestPath("/layer9/investigations/{caseId}/suspend")
    @RolesAllowed({"aml-compliance", "aml-mlro"})
    @RestStatus(204)
    public void suspendInvestigation(@PathParam UUID caseId) {
        caseHubRuntime.suspendCase(caseId);
    }

    @PlatformMutation("Resume a suspended investigation")
    @RestPath("/layer9/investigations/{caseId}/resume")
    @RolesAllowed({"aml-compliance", "aml-mlro"})
    @RestStatus(204)
    public void resumeInvestigation(@PathParam UUID caseId) {
        caseHubRuntime.resumeCase(caseId);
    }
}
