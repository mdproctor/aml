package io.casehub.aml.memory;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.api.model.event.CaseHubEventType;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.memory.CaseMemoryStore;
import io.casehub.platform.api.memory.MemoryAttributeKeys;
import io.casehub.platform.api.memory.MemoryInput;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Layer 8: verifies that prior entity context drives senior analyst routing.
 *
 * <p>Uses unique account IDs per test to avoid InMemoryMemoryStore cross-test pollution.
 * The senior-analyst-review capability is served by 'senior-analyst-agent' per AmlTrustScoreSeeder.
 */
@QuarkusTest
class AmlLayer8RoutingTest {

    @Inject CaseMemoryStore memoryStore;
    @Inject CaseHubRuntime caseHubRuntime;

    private static final Duration TIMEOUT             = Duration.ofSeconds(15);
    private static final Duration POLL_INTERVAL       = Duration.ofMillis(100);
    private static final String   TENANT              = TenancyConstants.DEFAULT_TENANT_ID;
    private static final String   SENIOR_ANALYST_WORKER = "senior-analyst-agent";

    private UUID startInvestigation(final String txId, final String originAccountId) {
        final var body = """
                {
                  "id": "%s",
                  "originAccountId": "%s",
                  "destinationAccountId": "ACC-L8-DEST-SHARED",
                  "amount": 50000,
                  "currency": "USD",
                  "timestamp": "2024-01-01T00:00:00Z",
                  "flagReason": "Unusual pattern"
                }
                """.formatted(txId, originAccountId);

        return UUID.fromString(
            given().contentType("application/json").body(body)
                .when().post("/api/layer6/investigations")
                .then().statusCode(202)
                .extract().path("caseId"));
    }

    private Set<String> scheduledWorkerNames(final UUID caseId) {
        return caseHubRuntime.eventLog(caseId, Set.of(CaseHubEventType.WORKER_SCHEDULED))
                .toCompletableFuture().join()
                .stream()
                .filter(r -> r.metadata() != null && r.metadata().has("workerName"))
                .map(r -> r.metadata().get("workerName").asText())
                .collect(Collectors.toSet());
    }

    private void storeHighConfidenceEntityRisk(final String accountId) {
        memoryStore.store(new MemoryInput(
            accountId, AmlMemoryDomains.ENTITY_RISK, TENANT, null,
            "Account " + accountId + " appeared in 3 prior SAR filings — high risk.",
            Map.of(MemoryAttributeKeys.CONFIDENCE, MemoryAttributeKeys.formatConfidence(0.9),
                   MemoryAttributeKeys.OUTCOME, "UPHELD")));
    }

    @Test
    void knownHighRiskEntity_seniorAnalystScheduled() {
        final String account = "ACC-L8-HR-" + UUID.randomUUID();
        storeHighConfidenceEntityRisk(account);
        UUID caseId = startInvestigation("TXN-L8-HR-" + UUID.randomUUID(), account);

        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL)
            .until(() -> scheduledWorkerNames(caseId).contains(SENIOR_ANALYST_WORKER));
    }

    @Test
    void noHistoryNonPepEntity_seniorAnalystNotScheduled() {
        // No memory pre-populated; entity stub returns CORPORATE riskScore=0.35 — binding must NOT fire.
        final String account = "ACC-L8-NOHIST-" + UUID.randomUUID();
        UUID caseId = startInvestigation("TXN-L8-NOHIST-" + UUID.randomUUID(), account);

        final AtomicReference<Set<String>> snapshot = new AtomicReference<>();
        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).until(() -> {
            Set<String> workers = scheduledWorkerNames(caseId);
            snapshot.set(workers);
            return workers.stream().anyMatch(w -> w.startsWith("sar-drafting-agent"));
        });

        assertFalse(snapshot.get().contains(SENIOR_ANALYST_WORKER),
            "Non-PEP entity with no history must not trigger senior analyst: " + snapshot.get());
    }

    @Test
    void lowConfidenceHistory_seniorAnalystNotScheduled() {
        // Confidence 0.7 < 0.8 threshold — must NOT trigger isKnownHighRisk
        final String account = "ACC-L8-LOWCONF-" + UUID.randomUUID();
        memoryStore.store(new MemoryInput(
            account, AmlMemoryDomains.ENTITY_RISK, TENANT, null,
            "Account " + account + " — moderate risk.",
            Map.of(MemoryAttributeKeys.CONFIDENCE, MemoryAttributeKeys.formatConfidence(0.7))));

        UUID caseId = startInvestigation("TXN-L8-LOWCONF-" + UUID.randomUUID(), account);

        final AtomicReference<Set<String>> snapshot = new AtomicReference<>();
        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).until(() -> {
            Set<String> workers = scheduledWorkerNames(caseId);
            snapshot.set(workers);
            return workers.stream().anyMatch(w -> w.startsWith("sar-drafting-agent"));
        });

        assertFalse(snapshot.get().contains(SENIOR_ANALYST_WORKER),
            "Low-confidence history must not trigger senior analyst: " + snapshot.get());
    }
}
