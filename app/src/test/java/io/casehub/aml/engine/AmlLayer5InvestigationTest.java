package io.casehub.aml.engine;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.api.model.event.CaseHubEventType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Integration tests verifying Layer 5 adaptive investigation paths.
 *
 * <p>Each test starts a case via HTTP, then uses Awaitility to poll the engine event log
 * until the expected workers have completed. This decouples HTTP response time (fast)
 * from investigation completion time (asynchronous via Quartz).
 */
@QuarkusTest
class AmlLayer5InvestigationTest {

    @Inject
    CaseHubRuntime caseHubRuntime;

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    private UUID startInvestigation(final String txId, final String flagReason) {
        final var body = """
                {
                  "id": "%s",
                  "originAccountId": "ACC-A",
                  "destinationAccountId": "ACC-B",
                  "amount": 50000,
                  "currency": "USD",
                  "timestamp": "2024-01-01T00:00:00Z",
                  "flagReason": "%s"
                }
                """.formatted(txId, flagReason);

        final var response = given()
                .contentType("application/json")
                .body(body)
                .when()
                .post("/api/layer5/investigations")
                .then()
                .statusCode(200)
                .extract()
                .as(Layer5InvestigationResponse.class);

        return response.caseId();
    }

    /**
     * Returns the set of worker names that were SCHEDULED for the given case.
     *
     * WORKER_SCHEDULED events carry "workerName" in metadata. WORKER_SCHEDULED is the
     * right event to poll: a worker is only scheduled when its binding condition is satisfied,
     * meaning all required prior workers have completed and written to context. Sar-drafting
     * being scheduled proves entity + pattern + osint all completed.
     */
    private Set<String> scheduledWorkerNames(final UUID caseId) {
        return caseHubRuntime.eventLog(caseId, Set.of(CaseHubEventType.WORKER_SCHEDULED))
                .toCompletableFuture()
                .join()
                .stream()
                .filter(r -> r.metadata() != null && r.metadata().has("workerName"))
                .map(r -> r.metadata().get("workerName").asText())
                .collect(Collectors.toSet());
    }

    @Test
    void investigationCompletes_allCoreWorkersRun() {
        final UUID caseId = startInvestigation("TXN-COMPLETE-001", "Unusual pattern");

        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).until(() -> {
            final var workers = scheduledWorkerNames(caseId);
            return workers.contains("entity-resolution-agent")
                    && workers.contains("pattern-analysis-agent")
                    && workers.contains("osint-screening-agent")
                    && (workers.contains("sar-drafting-agent-senior") || workers.contains("sar-drafting-agent-junior"));
        });
    }

    @Test
    void pepTransaction_seniorAnalystBindingFires() {
        final UUID caseId = startInvestigation("TXN-PEP-001",
                "PEP entity detected — high risk transfer");

        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).until(() ->
                scheduledWorkerNames(caseId).contains("senior-analyst-agent"));
    }

    @Test
    void nonPepTransaction_seniorAnalystDoesNotFire() {
        final UUID caseId = startInvestigation("TXN-CORP-001",
                "Structuring pattern detected");

        // Wait for sar-drafting to be scheduled (proves entity+pattern+osint completed).
        // Capture the snapshot inside Awaitility to avoid a re-query race window.
        final var workers = new java.util.concurrent.atomic.AtomicReference<Set<String>>();
        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).until(() -> {
            final var current = scheduledWorkerNames(caseId);
            workers.set(current);
            return current.contains("sar-drafting-agent-senior") || current.contains("sar-drafting-agent-junior");
        });

        assertTrue(!workers.get().contains("senior-analyst-agent"),
                "Non-PEP transaction must not trigger senior analyst: " + workers.get());
    }

    @Test
    void patternAndOsintRunInParallel_bothFireAfterEntity() {
        final UUID caseId = startInvestigation("TXN-PARALLEL-001",
                "Suspicious cross-border transfer");

        // Wait until both have completed — they should fire simultaneously after entity
        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).until(() -> {
            final var workers = scheduledWorkerNames(caseId);
            return workers.contains("pattern-analysis-agent")
                    && workers.contains("osint-screening-agent");
        });

        final var workers = scheduledWorkerNames(caseId);
        assertTrue(workers.contains("entity-resolution-agent"),
                "Entity resolution must have fired before pattern/osint");
    }

    @Test
    void osintDecline_doesNotBlockSarDrafting() {
        // OSINT always declines in stubs — verify SAR still drafts successfully
        final UUID caseId = startInvestigation("TXN-OSINT-DECLINE-001",
                "Structuring below reporting threshold");

        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).until(() ->
                scheduledWorkerNames(caseId).stream().anyMatch(w -> w.startsWith("sar-drafting-agent")));
    }
}
