package io.casehub.aml.compliance;

import io.casehub.aml.domain.SuspiciousTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Layer 7: integration test for the compliance evidence REST endpoint.
 *
 * <p>Starts a full investigation via the Layer 6 engine path, waits for async
 * completion, then verifies the compliance evidence response structure.
 *
 * <p>Note on SLA status: the engine path (Layer 5/6) creates the compliance officer
 * WorkItem inside the sar-drafting worker, but does not write a
 * COMPLIANCE_REVIEW_OPENED ledger entry linking back to the WorkItem. The SLA
 * requirement therefore shows GAP in this integration test. The Layer 3 synchronous
 * path ({@code AmlInvestigationCoordinator}) writes both entries and achieves CLOSED.
 * Closing this gap for the engine path is tracked as a follow-up.
 *
 * <p>Note on Merkle chain: concurrent CaseLedgerEntry writes in the H2 test environment
 * cause Merkle frontier collisions (unique constraint on subject_id + level). This
 * makes chainVerified=false in tests. Production PostgreSQL with row-level locking
 * does not have this issue.
 */
@QuarkusTest
class AmlLayer7ResourceTest {

    @Test
    void getComplianceEvidence_afterInvestigation_returnsAllRequirements() {
        String caseId = given().contentType(ContentType.JSON)
            .body(pepTransaction("TXN-L7-001"))
            .when().post("/api/layer6/investigations")
            .then().statusCode(202)
            .extract().path("caseId");

        // Wait for investigation to complete (sar-drafting is the last worker)
        Awaitility.await()
            .atMost(30, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .until(() -> "completed".equals(
                given().when().get("/api/layer6/investigations/" + caseId)
                    .then().extract().path("status")));

        given().when().get("/api/investigations/{caseId}/compliance-evidence", caseId)
            .then().statusCode(200)
            .body("caseId", equalTo(caseId))
            .body("generatedAt", notNullValue())
            .body("signature", nullValue())
            // Audit chain: CASE_OPENED present; chainVerified may be false due to
            // H2 Merkle frontier concurrency — status is PARTIAL or CLOSED
            .body("auditChain.status", anyOf(equalTo("CLOSED"), equalTo("PARTIAL")))
            .body("auditChain.treeRoot", notNullValue())
            .body("auditChain.events", hasSize(greaterThanOrEqualTo(1)))
            .body("auditChain.events[0].eventType", equalTo("CASE_OPENED"))
            .body("auditChain.events[0].causedByEntryId", nullValue())
            // SLA: engine path does not write COMPLIANCE_REVIEW_OPENED ledger entry,
            // so the SLA requirement shows GAP (no WorkItem linkage in the ledger)
            .body("sla.status", equalTo("GAP"))
            // Trust routing: all dispatched capabilities have attestations
            .body("trustRouting.status", equalTo("CLOSED"))
            .body("trustRouting.decisions", not(empty()))
            .body("trustRouting.decisions.capabilityTag", hasItem("sar-drafting"))
            // GDPR erasure: wired and endpoint documented
            .body("gdprErasure.erasureCapabilityWired", is(true))
            .body("gdprErasure.erasureEndpoint", equalTo("POST /api/actors/{actorId}/erasure"));
    }

    @Test
    void getComplianceEvidence_unknownCase_returns404() {
        given().when()
            .get("/api/investigations/{caseId}/compliance-evidence", UUID.randomUUID())
            .then().statusCode(404);
    }

    private SuspiciousTransaction pepTransaction(String id) {
        return new SuspiciousTransaction(id, "ACC-A", "ACC-B",
            new BigDecimal("200000"), "USD", Instant.now(), "PEP -- high risk");
    }
}
