package io.casehub.aml;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.aml.domain.AmlInvestigationResult;
import io.casehub.aml.domain.InvestigationSummary;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.ledger.AmlLedgerService;

@ApplicationScoped
public class AmlInvestigationCoordinator implements AmlInvestigationApplicationService {

    private final AmlInvestigator investigator;
    private final ComplianceReviewLifecycle compliance;
    private final AmlLedgerService ledgerService;

    @Inject
    public AmlInvestigationCoordinator(final AmlInvestigator investigator,
            final ComplianceReviewLifecycle compliance,
            final AmlLedgerService ledgerService) {
        this.investigator = investigator;
        this.compliance = compliance;
        this.ledgerService = ledgerService;
    }

    @Override
    public AmlInvestigationResult investigate(final SuspiciousTransaction transaction) {
        // Layer 4: generate a case UUID — shared subjectId for all ledger entries
        final UUID caseId = UUID.randomUUID();

        // Layer 4: record investigation start in the tamper-evident ledger
        final UUID caseEntryId = ledgerService.writeCaseOpened(transaction, caseId);

        // Layer 3: dispatch typed COMMANDs to specialist agents
        final InvestigationSummary summary = investigator.investigate(transaction, caseId);

        // Layer 2: open compliance officer WorkItem with 30-day FinCEN SLA
        final String taskId = compliance.openReview(transaction, summary);

        // Layer 4: record compliance review opening
        ledgerService.writeComplianceReviewOpened(caseId, taskId);

        return new AmlInvestigationResult(summary, taskId, caseId, caseEntryId);
    }
}
