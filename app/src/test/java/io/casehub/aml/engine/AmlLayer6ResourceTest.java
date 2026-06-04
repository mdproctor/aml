package io.casehub.aml.engine;

import io.casehub.aml.domain.SarOutcome;
import io.casehub.aml.domain.SarVerdict;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class AmlLayer6ResourceTest {

    private static final SuspiciousTransaction TRANSACTION = new SuspiciousTransaction(
            "TXN-L6-" + UUID.randomUUID(),
            "ACC-ORIGIN", "ACC-DEST",
            new BigDecimal("95000"), "USD",
            Instant.parse("2024-06-01T00:00:00Z"),
            "Structured layering pattern — CORPORATE");

    @Test
    void post_investigate_returns_202_with_caseId() {
        final String caseIdStr = given().contentType(ContentType.JSON).body(TRANSACTION)
                .when().post("/api/layer6/investigations")
                .then().statusCode(202)
                .extract().path("caseId");
        assertNotNull(caseIdStr, "caseId must not be null");
        // Drain: wait for completion to prevent Quartz contamination of subsequent tests.
        Awaitility.await().atMost(15, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().when().get("/api/layer6/investigations/" + caseIdStr)
                                .then().extract().path("status")));
    }

    @Test
    void get_investigation_returns_completed_with_routing_decisions() {
        final String caseIdStr = given().contentType(ContentType.JSON).body(TRANSACTION)
                .when().post("/api/layer6/investigations")
                .then().statusCode(202)
                .extract().path("caseId");

        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().when().get("/api/layer6/investigations/" + caseIdStr)
                                .then().statusCode(200).extract().path("status")));

        given().when().get("/api/layer6/investigations/" + caseIdStr)
                .then().statusCode(200)
                .body("status", equalTo("completed"))
                .body("routingDecisions", not(empty()))
                .body("routingDecisions.capabilityTag", hasItem("sar-drafting"))
                .body("routingDecisions.selectedWorker", hasItem("sar-drafting-agent-senior"));
    }

    @Test
    void post_outcome_with_invalid_score_returns_400_with_domain_message() {
        // investigationAccuracyScore=1.5 violates SarOutcome compact constructor invariant
        given().contentType(ContentType.JSON)
                .body("{\"verdict\":\"UPHELD\",\"reason\":\"test\",\"investigationAccuracyScore\":1.5}")
                .when().post("/api/layer6/investigations/" + UUID.randomUUID() + "/outcome")
                .then().statusCode(400)
                .body("error", containsString("investigationAccuracyScore"));
    }

    @Test
    void post_outcome_returns_204() {
        final String caseIdStr = given().contentType(ContentType.JSON).body(TRANSACTION)
                .when().post("/api/layer6/investigations")
                .then().statusCode(202)
                .extract().path("caseId");

        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().when().get("/api/layer6/investigations/" + caseIdStr)
                                .then().extract().path("status")));

        given().contentType(ContentType.JSON)
                .body(new SarOutcome(SarVerdict.UPHELD, "SAR upheld by FinCEN", 0.95))
                .when().post("/api/layer6/investigations/" + caseIdStr + "/outcome")
                .then().statusCode(204);
    }
}
