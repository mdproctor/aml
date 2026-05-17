package io.casehub.aml.tutorial;

import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SpecialistOutcome;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.investigation.SarDraftingService;

class NaiveSarDraftingService implements SarDraftingService {

    @Override
    public String draft(SuspiciousTransaction transaction,
                        SpecialistOutcome<EntityResolutionResult> entity,
                        SpecialistOutcome<PatternAnalysisResult>  pattern,
                        SpecialistOutcome<OsintResult>            osint) {
        String entitySummary = switch (entity) {
            case SpecialistOutcome.Completed<EntityResolutionResult> c ->
                    "Entity: " + c.result().entityId();
            case SpecialistOutcome.Declined<EntityResolutionResult> d ->
                    "Entity resolution declined: " + d.reason();
            case SpecialistOutcome.Failed<EntityResolutionResult> f ->
                    "Entity resolution failed: " + f.reason();
        };
        String patternSummary = switch (pattern) {
            case SpecialistOutcome.Completed<PatternAnalysisResult> c ->
                    "Pattern: " + (c.result().structuringDetected() ? "structuring detected" : "no pattern");
            case SpecialistOutcome.Declined<PatternAnalysisResult> d ->
                    "Pattern analysis declined: " + d.reason();
            case SpecialistOutcome.Failed<PatternAnalysisResult> f ->
                    "Pattern analysis failed: " + f.reason();
        };
        String osintSummary = switch (osint) {
            case SpecialistOutcome.Completed<OsintResult> c ->
                    c.result().sanctionsHit() ? "OSINT: sanctions hit" : "OSINT: no sanctions match";
            case SpecialistOutcome.Declined<OsintResult> d ->
                    "OSINT screening declined (" + d.capability() + "): " + d.reason();
            case SpecialistOutcome.Failed<OsintResult> f ->
                    "OSINT screening failed: " + f.reason() + " — manual review required";
        };
        return "SAR narrative for " + transaction.id() + ". " + entitySummary + ". " + patternSummary + ". " + osintSummary + ".";
    }
}
