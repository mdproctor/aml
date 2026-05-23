package io.casehub.aml.tutorial;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.aml.AmlInvestigator;
import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.InvestigationSummary;
import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SpecialistOutcome;
import io.casehub.aml.domain.SuspiciousTransaction;

@ApplicationScoped
@DefaultBean
public class NaiveAmlInvestigationService implements AmlInvestigator {

    private final NaiveEntityResolutionService entityResolutionService = new NaiveEntityResolutionService();
    private final NaivePatternAnalysisService  patternAnalysisService  = new NaivePatternAnalysisService();
    private final NaiveOsintScreeningService   osintScreeningService   = new NaiveOsintScreeningService();
    private final NaiveSarDraftingService      sarDraftingService      = new NaiveSarDraftingService();

    @Override
    public InvestigationSummary investigate(SuspiciousTransaction transaction, java.util.UUID caseId) {
        // LAYER 4 GAP: caseId is not used here — Layer 1 has no tamper-evident audit trail.
        // LAYER 1 GAP: no attribution — who resolved this entity graph?
        // No record of which agent made this decision or when.
        EntityResolutionResult entityResult = entityResolutionService.resolve(transaction);

        // LAYER 1 GAP: no failure resilience — if this call times out or throws,
        // the entire investigation is lost with no trace of partial work.
        PatternAnalysisResult patternResult = patternAnalysisService.analyze(transaction);

        // LAYER 1 GAP: no deadline tracking — OSINT runs sequentially after pattern
        // analysis. No FinCEN 30-day SLA. No parallel execution. No formal obligation.
        OsintResult osintResult = osintScreeningService.screen(transaction);
        SpecialistOutcome<OsintResult> osintOutcome = new SpecialistOutcome.Completed<>(osintResult);

        // LAYER 1 GAP: no audit trail — this narrative cannot be proven to FinCEN.
        // No tamper-evident record of the reasoning chain exists.
        String sarNarrative = sarDraftingService.draft(
                transaction,
                new SpecialistOutcome.Completed<>(entityResult),
                new SpecialistOutcome.Completed<>(patternResult),
                osintOutcome);

        return new InvestigationSummary(
                transaction,
                new SpecialistOutcome.Completed<>(entityResult),
                new SpecialistOutcome.Completed<>(patternResult),
                osintOutcome,
                sarNarrative);
    }
}
