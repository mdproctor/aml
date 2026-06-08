package io.casehub.aml.engine;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;
import io.casehub.api.model.Capability;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.Worker;
import io.casehub.api.model.WorkerExecutionContext;
import io.casehub.api.model.WorkerResult;
import io.casehub.api.engine.YamlCaseHub;
import io.casehub.aml.ComplianceReviewLifecycle;
import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.InvestigationSummary;
import io.casehub.aml.domain.OsintResult;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SpecialistOutcome;
import io.casehub.aml.domain.SuspiciousTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Layer 5 case hub — loads the AML investigation YAML definition and augments it
 * with in-process worker functions that delegate to existing Layer 3/4 stubs.
 *
 * <p>Workers are lambda functions that run on Quartz worker threads (not Vert.x IO
 * threads), so JPA calls inside the SAR-drafting worker are safe.
 */
@ApplicationScoped
public class AmlInvestigationCaseHub extends YamlCaseHub {

    private static final Logger LOG = Logger.getLogger(AmlInvestigationCaseHub.class);

    @Inject
    ComplianceReviewLifecycle complianceReviewLifecycle;

    @Inject
    ObjectMapper objectMapper;

    private volatile CaseDefinition augmentedDefinition;

    public AmlInvestigationCaseHub() {
        super("aml/aml-investigation.yaml");
    }

    @Override
    public CaseDefinition getDefinition() {
        if (augmentedDefinition == null) {
            synchronized (this) {
                if (augmentedDefinition == null) {
                    augmentedDefinition = augment(super.getDefinition());
                }
            }
        }
        return augmentedDefinition;
    }

    private CaseDefinition augment(final CaseDefinition yaml) {
        // Called exactly once, from inside synchronized(this). YamlCaseHub.getWorkers()
        // returns an empty mutable list (the YAML has no workers: section). addAll is safe
        // because the synchronized block prevents concurrent augmentation.
        yaml.getWorkers().addAll(List.of(
                entityResolutionWorker(),
                patternAnalysisWorker(),
                osintScreeningWorker(),           // junior — declines on PEP clearance
                osintScreeningWorkerSenior(),     // senior — full clearance, no decline
                seniorAnalystWorker(),
                sarDraftingWorkerJunior(),        // minimal narrative
                sarDraftingWorkerSenior()         // full narrative
        ));
        return yaml;
    }

    private static Capability cap(final String name) {
        return Capability.builder().name(name).inputSchema(".").outputSchema(".").build();
    }

    private Worker entityResolutionWorker() {
        return Worker.builder()
                .name("entity-resolution-agent")
                .capabilities(List.of(cap("entity-resolution")))
                .function((final Map<String, Object> input) -> {
                    @SuppressWarnings("unchecked")
                    final Map<String, Object> tx = (Map<String, Object>) input.get("transaction");
                    final String flagReason = tx != null
                            ? (String) tx.getOrDefault("flagReason", "") : "";
                    final boolean isPep = flagReason != null && flagReason.contains("PEP");
                    final String txId = tx != null
                            ? String.valueOf(tx.getOrDefault("id", "unknown")) : "unknown";
                    return WorkerResult.of(Map.of(
                            "entityId", "entity-" + txId,
                            "ownershipChain", isPep
                                    ? "Direct → PEP Principal"
                                    : "Direct → Corporate Entity",
                            "entityType", isPep ? "PEP" : "CORPORATE",
                            "riskScore", isPep ? 0.87 : 0.35
                    ));
                })
                .build();
    }

    private Worker patternAnalysisWorker() {
        return Worker.builder()
                .name("pattern-analysis-agent")
                .capabilities(List.of(cap("pattern-analysis")))
                .function((final Map<String, Object> input) -> WorkerResult.of(Map.of(
                        "structuringDetected", false,
                        "description", "No structuring pattern detected in transaction cluster"
                )))
                .build();
    }

    /**
     * OSINT screening always declines in Layer 5 stubs — demonstrates that DECLINE
     * is a first-class outcome: osintScreening.declined=true satisfies the sar-drafting
     * binding condition (osintScreening != null) so the investigation continues normally.
     */
    private Worker osintScreeningWorker() {
        return Worker.builder()
                .name("osint-screening-agent")
                .capabilities(List.of(cap("osint-screening")))
                .function((final Map<String, Object> input) -> WorkerResult.of(Map.of(
                        "declined", true,
                        "reason", "insufficient clearance for PEP database access",
                        "pepHit", false,
                        "sanctionsHit", false
                )))
                .build();
    }

    /**
     * Senior OSINT worker — full clearance, never declines. Demonstrates trust-based
     * routing: complex or PEP cases are routed to this worker rather than the junior.
     */
    private Worker osintScreeningWorkerSenior() {
        return Worker.builder()
                .name("osint-screening-agent-senior")
                .capabilities(List.of(cap("osint-screening")))
                .function((final Map<String, Object> input) -> WorkerResult.of(Map.of(
                        "declined", false,
                        "reason", "full-clearance",
                        "pepHit", false,
                        "sanctionsHit", false,
                        "screeningLevel", "ENHANCED"
                )))
                .build();
    }

    private Worker seniorAnalystWorker() {
        return Worker.builder()
                .name("senior-analyst-agent")
                .capabilities(List.of(cap("senior-analyst-review")))
                .function((final Map<String, Object> input) -> WorkerResult.of(Map.of(
                        "reviewed", true,
                        "recommendation",
                        "PEP entity confirmed — enhanced due diligence required."
                                + " Escalate to compliance director."
                )))
                .build();
    }

    /**
     * Junior SAR drafting worker — minimal narrative, suitable for routine cases.
     * Opens the compliance officer WorkItem (Layer 2 — 30-day FinCEN SLA).
     * Runs on a Quartz worker thread; JPA calls via ComplianceReviewLifecycle are safe here.
     */
    private Worker sarDraftingWorkerJunior() {
        return Worker.builder()
                .name("sar-drafting-agent-junior")
                .capabilities(List.of(cap("sar-drafting")))
                .function((final Map<String, Object> input) -> {
                    @SuppressWarnings("unchecked")
                    final Map<String, Object> txMap = (Map<String, Object>) input.get("transaction");
                    final SuspiciousTransaction tx =
                            objectMapper.convertValue(txMap, SuspiciousTransaction.class);
                    @SuppressWarnings("unchecked")
                    final Map<String, Object> osintMap =
                            (Map<String, Object>) input.get("osintScreening");
                    final boolean osintDeclined = osintMap != null
                            && Boolean.TRUE.equals(osintMap.get("declined"));
                    final String sarNarrative = "SAR filed for transaction " + tx.id()
                            + ". Amount: " + tx.amount() + " " + tx.currency()
                            + (osintDeclined ? " OSINT screening declined." : "");
                    // caseId is always available via WorkerExecutionContext — no signal needed.
                    final UUID caseId = WorkerExecutionContext.current().caseId();
                    final String complianceTaskId =
                            complianceReviewLifecycle.openReview(tx, buildSummary(input, tx, sarNarrative), caseId);
                    return WorkerResult.of(Map.of("sarNarrative", sarNarrative, "complianceTaskId", complianceTaskId));
                })
                .build();
    }

    /**
     * Senior SAR drafting worker — full narrative including entity type and flag reason.
     * Used for complex or PEP cases routed via trust-weighted selection.
     * Opens the compliance officer WorkItem (Layer 2 — 30-day FinCEN SLA).
     */
    private Worker sarDraftingWorkerSenior() {
        return Worker.builder()
                .name("sar-drafting-agent-senior")
                .capabilities(List.of(cap("sar-drafting")))
                .function((final Map<String, Object> input) -> {
                    @SuppressWarnings("unchecked")
                    final Map<String, Object> txMap = (Map<String, Object>) input.get("transaction");
                    @SuppressWarnings("unchecked")
                    final Map<String, Object> entityMap =
                            (Map<String, Object>) input.get("entityResolution");
                    @SuppressWarnings("unchecked")
                    final Map<String, Object> osintMap =
                            (Map<String, Object>) input.get("osintScreening");
                    final SuspiciousTransaction tx =
                            objectMapper.convertValue(txMap, SuspiciousTransaction.class);
                    final String entityType = entityMap != null
                            ? (String) entityMap.getOrDefault("entityType", "UNKNOWN")
                            : "UNKNOWN";
                    final boolean osintDeclined = osintMap != null
                            && Boolean.TRUE.equals(osintMap.get("declined"));
                    final String sarNarrative = buildNarrative(tx, entityType, osintDeclined);
                    // caseId is always available via WorkerExecutionContext — no signal needed.
                    final UUID caseIdSenior = WorkerExecutionContext.current().caseId();
                    final String complianceTaskId =
                            complianceReviewLifecycle.openReview(tx, buildSummary(input, tx, sarNarrative), caseIdSenior);
                    return WorkerResult.of(Map.of("sarNarrative", sarNarrative, "complianceTaskId", complianceTaskId));
                })
                .build();
    }

    /**
     * Builds the {@link InvestigationSummary} passed to
     * {@link ComplianceReviewLifecycle#openReview}. Shared by both SAR drafting workers
     * to avoid duplication. {@code tx} is passed in pre-deserialized to avoid a second
     * {@code objectMapper.convertValue} call when the worker already holds the object.
     */
    private InvestigationSummary buildSummary(
            final Map<String, Object> input,
            final SuspiciousTransaction tx,
            final String sarNarrative) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> entityMap = (Map<String, Object>) input.get("entityResolution");
        @SuppressWarnings("unchecked")
        final Map<String, Object> osintMap = (Map<String, Object>) input.get("osintScreening");
        final boolean osintDeclined = osintMap != null && Boolean.TRUE.equals(osintMap.get("declined"));
        final SpecialistOutcome<EntityResolutionResult> entityOutcome = entityMap != null
                ? new SpecialistOutcome.Completed<>(
                        objectMapper.convertValue(entityMap, EntityResolutionResult.class))
                : new SpecialistOutcome.Declined<>(
                        "sar-agent", "entity-resolution", "missing from context");
        final SpecialistOutcome<PatternAnalysisResult> patternOutcome =
                new SpecialistOutcome.Completed<>(
                        new PatternAnalysisResult(false, "engine-driven investigation"));
        final SpecialistOutcome<OsintResult> osintOutcome = osintDeclined
                ? new SpecialistOutcome.Declined<>(
                        "osint-agent", "osint-screening",
                        "insufficient clearance for PEP database access")
                : new SpecialistOutcome.Completed<>(new OsintResult(false, false, "no matches"));
        return new InvestigationSummary(tx, entityOutcome, patternOutcome, osintOutcome, sarNarrative);
    }

    private static String buildNarrative(
            final SuspiciousTransaction tx,
            final String entityType,
            final boolean osintDeclined) {
        final String osintNote = osintDeclined
                ? " OSINT screening declined (insufficient clearance for PEP database access)."
                : "";
        return "SAR narrative for transaction " + tx.id()
                + ". Entity type: " + entityType
                + ". Amount: " + tx.amount() + " " + tx.currency()
                + ". Flag reason: " + tx.flagReason()
                + "." + osintNote;
    }
}
