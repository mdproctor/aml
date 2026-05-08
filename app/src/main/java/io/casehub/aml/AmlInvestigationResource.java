package io.casehub.aml;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.casehub.aml.domain.InvestigationSummary;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.tutorial.NaiveAmlInvestigationService;

@Path("/api/investigations")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AmlInvestigationResource {

    @POST
    public InvestigationSummary investigate(SuspiciousTransaction transaction) {
        return new NaiveAmlInvestigationService().investigate(transaction);
    }
}
