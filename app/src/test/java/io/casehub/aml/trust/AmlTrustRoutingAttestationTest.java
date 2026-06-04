package io.casehub.aml.trust;

import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.engine.AmlEngineCoordinator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Layer 7: verifies that AmlTrustRoutingObserver writes attestation entries for each
 * worker dispatch, capturing the trust score from TrustScoreCache at routing time.
 *
 * <p>Note: {@code @TestTransaction} is intentionally omitted — same pattern as
 * {@code AmlLedgerChainTest}. Tests use unique transaction IDs to avoid cross-test
 * interference with persisted attestation entries.
 */
@QuarkusTest
class AmlTrustRoutingAttestationTest {

    @Inject AmlEngineCoordinator coordinator;
    @Inject AmlTrustAttestationRepository attestationRepo;

    private void drain(final UUID caseId) {
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100))
            .until(() -> "completed".equals(
                given().when().get("/api/layer6/investigations/" + caseId)
                        .then().extract().path("status")));
    }

    @Test
    void workerDispatch_writesAttestationPerCapability() {
        UUID caseId = coordinator.startInvestigation(pep("TXN-ATT-001-" + UUID.randomUUID()));

        Awaitility.await().atMost(15, TimeUnit.SECONDS).until(() ->
            !attestationRepo.findByInvestigationCaseId(caseId).isEmpty()
        );

        List<AmlTrustRoutingAttestation> attestations =
            attestationRepo.findByInvestigationCaseId(caseId);

        assertFalse(attestations.isEmpty(), "At least one attestation must be written");

        for (AmlTrustRoutingAttestation a : attestations) {
            assertEquals(caseId, a.investigationCaseId);
            assertNotNull(a.capabilityTag, "capabilityTag must be set");
            assertNotNull(a.selectedWorkerId, "selectedWorkerId must be set");
            assertTrue(a.thresholdApplied > 0.0, "thresholdApplied must be positive");
        }
        drain(caseId);
    }

    @Test
    void workerDispatch_sarDraftingAttestation_hasNonNullScore_whenCacheSeeded() {
        UUID caseId = coordinator.startInvestigation(pep("TXN-ATT-002-" + UUID.randomUUID()));

        Awaitility.await().atMost(15, TimeUnit.SECONDS).until(() ->
            attestationRepo.findByInvestigationCaseId(caseId).stream()
                .anyMatch(a -> "sar-drafting".equals(a.capabilityTag))
        );

        AmlTrustRoutingAttestation sarAttestation = attestationRepo
            .findByInvestigationCaseId(caseId).stream()
            .filter(a -> "sar-drafting".equals(a.capabilityTag))
            .findFirst().orElseThrow(() -> new AssertionError("No sar-drafting attestation"));

        assertNotNull(sarAttestation.trustScoreAtRouting,
            "trustScoreAtRouting must be non-null when cache is seeded");
        assertTrue(sarAttestation.trustScoreAtRouting > 0.0,
            "trustScoreAtRouting must be positive when seeded");
        drain(caseId);
    }

    private SuspiciousTransaction pep(String id) {
        return new SuspiciousTransaction(id, "ACC-A", "ACC-B",
            new BigDecimal("200000"), "USD", Instant.now(), "PEP — high risk transfer");
    }
}
