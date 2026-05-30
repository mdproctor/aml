package io.casehub.aml.compliance;

import io.casehub.aml.domain.SuspiciousTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Layer 7: verifies GDPR erasure endpoint wiring.
 *
 * <p>Erases "aml-orchestrator" -- the system actor that wrote CASE_OPENED and
 * COMPLIANCE_REVIEW_OPENED entries. This proves the erasure mechanism works
 * end-to-end. A proper human-actor erasure scenario requires casehubio/aml#44.
 *
 * <p>Tokenisation must be enabled in test application.properties for this to work
 * (casehub.ledger.identity.tokenisation.enabled=true).
 */
@QuarkusTest
class AmlLayer7ErasureTest {

    @Test
    void eraseActor_systemActor_returnsMappingFoundAndCount() {
        // Run an investigation to ensure aml-orchestrator has ledger entries
        String caseId = given().contentType(ContentType.JSON)
            .body(new SuspiciousTransaction("TXN-ERASE-001", "ACC-A", "ACC-B",
                new BigDecimal("50000"), "USD", Instant.now(), "Structuring"))
            .when().post("/api/layer6/investigations")
            .then().statusCode(202)
            .extract().path("caseId");

        // Wait for investigation to complete (compliance evidence requires ledger entries)
        Awaitility.await()
            .atMost(30, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .until(() -> "completed".equals(
                given().when().get("/api/layer6/investigations/" + caseId)
                    .then().extract().path("status")));

        // Erase -- tokenisation enabled in test config
        given().contentType(ContentType.JSON).when()
            .post("/api/actors/{actorId}/erasure", "aml-orchestrator")
            .then().statusCode(200)
            .body("rawActorId", equalTo("aml-orchestrator"))
            .body("mappingFound", is(true))
            .body("affectedEntryCount", greaterThanOrEqualTo(1));
    }

    @Test
    void eraseActor_unknownActor_returnsMappingFalse() {
        given().contentType(ContentType.JSON).when()
            .post("/api/actors/{actorId}/erasure", "actor-that-does-not-exist")
            .then().statusCode(200)
            .body("mappingFound", is(false))
            .body("affectedEntryCount", equalTo(0));
    }
}
