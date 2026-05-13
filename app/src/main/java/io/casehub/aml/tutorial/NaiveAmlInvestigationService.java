package io.casehub.aml.tutorial;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.aml.AmlInvestigationApplicationService;
import io.casehub.aml.domain.AmlInvestigationResult;
import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.InvestigationSummary;
import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SuspiciousTransaction;

@ApplicationScoped
@DefaultBean
public class NaiveAmlInvestigationService implements AmlInvestigationApplicationService {

    private final NaiveEntityResolutionService entityResolutionService = new NaiveEntityResolutionService();
    private final NaivePatternAnalysisService  patternAnalysisService  = new NaivePatternAnalysisService();
    private final NaiveOsintScreeningService   osintScreeningService   = new NaiveOsintScreeningService();
    private final NaiveSarDraftingService      sarDraftingService      = new NaiveSarDraftingService();

    @Override
    public AmlInvestigationResult investigate(SuspiciousTransaction transaction) {
        // LAYER 1 GAP: no attribution — who resolved this entity graph?
        // No record of which agent made this decision or when.
        EntityResolutionResult entity = entityResolutionService.resolve(transaction);

        // LAYER 1 GAP: no failure resilience — if this call times out or throws,
        // the entire investigation is lost with no trace of partial work.
        PatternAnalysisResult pattern = patternAnalysisService.analyze(transaction);

        // LAYER 1 GAP: no deadline tracking — OSINT runs sequentially after pattern
        // analysis. No FinCEN 30-day SLA. No parallel execution. No formal obligation.
        OsintResult osint = osintScreeningService.screen(transaction);

        // LAYER 1 GAP: no audit trail — this narrative cannot be proven to FinCEN.
        // No tamper-evident record of the reasoning chain exists.
        String sarNarrative = sarDraftingService.draft(transaction, entity, pattern, osint);

        InvestigationSummary summary = new InvestigationSummary(transaction, entity, pattern, osint, sarNarrative);
        return new AmlInvestigationResult(summary, null);
    }
}
