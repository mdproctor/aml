package io.casehub.aml.tutorial;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.aml.AmlInvestigationApplicationService;
import io.casehub.aml.domain.AmlInvestigationResult;
import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.InvestigationSummary;
import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemCreateRequest;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.service.WorkItemService;

@ApplicationScoped
public class WorkItemAmlInvestigationService implements AmlInvestigationApplicationService {

    @Inject
    WorkItemService workItemService;

    private final NaiveEntityResolutionService entityResolutionService = new NaiveEntityResolutionService();
    private final NaivePatternAnalysisService  patternAnalysisService  = new NaivePatternAnalysisService();
    private final NaiveOsintScreeningService   osintScreeningService   = new NaiveOsintScreeningService();
    private final NaiveSarDraftingService      sarDraftingService      = new NaiveSarDraftingService();

    @Override
    public AmlInvestigationResult investigate(SuspiciousTransaction transaction) {
        EntityResolutionResult entity  = entityResolutionService.resolve(transaction);
        PatternAnalysisResult  pattern = patternAnalysisService.analyze(transaction);
        OsintResult            osint   = osintScreeningService.screen(transaction);
        String sarNarrative            = sarDraftingService.draft(transaction, entity, pattern, osint);

        InvestigationSummary summary = new InvestigationSummary(transaction, entity, pattern, osint, sarNarrative);

        // LAYER 2: create a compliance officer WorkItem with the FinCEN 30-day claim SLA.
        // The compliance officer has 30 days from investigation completion to review and file.
        WorkItem workItem = workItemService.create(new WorkItemCreateRequest(
                "Compliance review — SAR for transaction " + transaction.id(),
                null,
                "aml-compliance",
                null,
                WorkItemPriority.HIGH,
                null,
                "compliance-officers",
                null,
                null,
                "aml-system",
                null,
                Instant.now().plus(30, ChronoUnit.DAYS),
                null,
                null,
                null,
                null,
                "aml:investigation/" + transaction.id(),
                null,
                null
        ));

        return new AmlInvestigationResult(summary, workItem.id.toString());
    }
}
