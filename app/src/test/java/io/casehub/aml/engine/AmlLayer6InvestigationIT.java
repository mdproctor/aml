package io.casehub.aml.engine;

import io.casehub.aml.domain.SarOutcome;
import io.casehub.aml.domain.SarVerdict;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Full end-to-end integration test for Layer 6 trust routing.
 *
 * <p>Verifies the complete flow: start an investigation via HTTP → wait for async
 * completion → confirm senior agent was selected via trust score → record SAR outcome →
 * confirm LedgerAttestation persisted with SOUND verdict and correct trust dimension.
 *
 * <p>This is a {@code @QuarkusTest} (not a separate IT module) because the Quartz
 * worker that drives the engine runs inside the same Quarkus application context.
 *
 * <p>Layer 6 tutorial component — ties together all trust routing primitives:
 * {@code AmlTrustScoreSeeder} seeds initial scores → {@code AmlTrustRoutingPolicyProvider}
 * supplies the threshold → engine selects senior worker → {@code SarOutcomeFeedbackService}
 * writes the attestation back.
 */
@QuarkusTest
class AmlLayer6InvestigationIT {

    @PersistenceContext(unitName = "qhorus")
    EntityManager em;

    @Test
    void full_trust_routing_flow_corporate_case() {
        final SuspiciousTransaction tx = new SuspiciousTransaction(
                "TXN-IT-" + UUID.randomUUID(),
                "ACC-A", "ACC-B",
                new BigDecimal("120000"), "USD",
                Instant.parse("2024-09-15T00:00:00Z"),
                "Structured layering — CORPORATE");

        // Step 1: Start investigation (async)
        final String caseIdStr = given().contentType(ContentType.JSON).body(tx)
                .when().post("/api/layer6/investigations")
                .then().statusCode(202)
                .extract().path("caseId");

        final UUID caseId = UUID.fromString(caseIdStr);

        // Step 2: Poll until completed (engine runs async on Quartz)
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().when().get("/api/layer6/investigations/" + caseIdStr)
                                .then().extract().path("status")));

        // Step 3: Verify senior sar-drafting agent was selected
        final io.restassured.response.Response getResponse =
                given().when().get("/api/layer6/investigations/" + caseIdStr)
                        .then().extract().response();
        final List<Map<String, Object>> decisions =
                getResponse.jsonPath().getList("routingDecisions");

        assertTrue(!decisions.isEmpty(), "Routing decisions must be populated");
        final Optional<Map<String, Object>> sarDecision = decisions.stream()
                .filter(d -> "sar-drafting".equals(d.get("capabilityTag")))
                .findFirst();
        assertTrue(sarDecision.isPresent(), "sar-drafting routing decision must be present");
        assertEquals("sar-drafting-agent-senior", sarDecision.get().get("selectedWorker"),
                "Senior agent must be selected (trust 0.90 > threshold 0.75)");
        assertNotNull(sarDecision.get().get("trustScore"), "Trust score must be in response");

        // Step 4: Record SAR outcome (positive)
        given().contentType(ContentType.JSON)
                .body(new SarOutcome(SarVerdict.UPHELD, "SAR upheld by FinCEN unit", 0.94))
                .when().post("/api/layer6/investigations/" + caseIdStr + "/outcome")
                .then().statusCode(204);

        // Step 5: Verify LedgerAttestation persisted with SOUND verdict
        final List<LedgerAttestation> attestations = em.createQuery(
                "SELECT a FROM LedgerAttestation a WHERE a.subjectId = :sid",
                LedgerAttestation.class)
                .setParameter("sid", caseId)
                .getResultList();
        assertFalse(attestations.isEmpty(), "LedgerAttestation must be written after outcome recorded");
        assertEquals(AttestationVerdict.SOUND, attestations.get(0).verdict);
        assertEquals("sar-drafting", attestations.get(0).capabilityTag);
        assertEquals("investigation-accuracy", attestations.get(0).trustDimension);
        assertEquals(0.94, attestations.get(0).dimensionScore, 0.001);
    }
}
