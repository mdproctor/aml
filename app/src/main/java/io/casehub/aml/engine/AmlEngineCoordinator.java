package io.casehub.aml.engine;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.ledger.AmlLedgerService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Layer 5 coordinator — starts a casehub-engine case for an AML investigation.
 *
 * <p>Returns the case UUID immediately; the engine drives the investigation asynchronously
 * via binding evaluation and Quartz worker execution. The caller can poll the event log
 * or the ledger to observe investigation progress.
 */
@ApplicationScoped
public class AmlEngineCoordinator {

    private static final Logger LOG = Logger.getLogger(AmlEngineCoordinator.class);
    private static final int CASE_START_TIMEOUT_SECONDS = 5;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Inject
    AmlInvestigationCaseHub caseHub;

    @Inject
    AmlLedgerService ledgerService;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Starts an AML investigation case and returns its UUID.
     *
     * <p>The case UUID is the stable identifier for the investigation — it links the
     * engine event log, the AML ledger entries, and the compliance officer WorkItem.
     */
    public UUID startInvestigation(final SuspiciousTransaction transaction) {
        final Map<String, Object> txMap = objectMapper.convertValue(transaction, MAP_TYPE);
        final Map<String, Object> initialContext = Map.of("transaction", txMap);

        final UUID caseId;
        try {
            // startCase() returns CompletionStage<UUID> — the UUID is the engine's internal
            // case instance ID. We block briefly to get it, then return; the investigation
            // proceeds asynchronously via Quartz worker execution.
            caseId = caseHub.startCase(initialContext)
                    .toCompletableFuture()
                    .get(CASE_START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to start AML investigation case for transaction %s",
                    transaction.id());
            throw new RuntimeException("Failed to start investigation case", e);
        }

        // Layer 4 continuity: record investigation start using the engine's case UUID so
        // ledger entries and engine event log share the same stable identifier.
        ledgerService.writeCaseOpened(transaction, caseId);
        LOG.infof("AML investigation started: caseId=%s txId=%s", caseId, transaction.id());
        return caseId;
    }
}
