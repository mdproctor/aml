package io.casehub.aml.engine;

import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class AmlLayer9ResourceTest {

    @Inject CaseInstanceCache caseInstanceCache;

    private static final SuspiciousTransaction CORPORATE_TX = new SuspiciousTransaction(
            "TXN-L9-RES-" + UUID.randomUUID(),
            "ACC-C", "ACC-D",
            new BigDecimal("50000"), "USD",
            Instant.parse("2024-12-01T00:00:00Z"),
            "Routine structured layering — CORPORATE");

    @Test
    void post_investigate_returns_202_with_caseId() {
        final String caseIdStr = given().contentType(ContentType.JSON).body(CORPORATE_TX)
                .when().post("/api/layer9/investigations")
                .then().statusCode(202)
                .extract().path("caseId");
        assertNotNull(caseIdStr, "caseId must not be null");
        Awaitility.await().atMost(15, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().when().get("/api/layer9/investigations/" + caseIdStr)
                                .then().extract().path("status")));
    }

    @Test
    void get_investigation_returns_completed_after_cache_eviction() {
        final String caseIdStr = given().contentType(ContentType.JSON).body(CORPORATE_TX)
                .when().post("/api/layer9/investigations")
                .then().statusCode(202)
                .extract().path("caseId");

        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> "completed".equals(
                        given().when().get("/api/layer9/investigations/" + caseIdStr)
                                .then().statusCode(200).extract().path("status")));

        // Simulate cache eviction — endpoint must fall back to CaseInstanceRepository
        caseInstanceCache.clear();

        given().when().get("/api/layer9/investigations/" + caseIdStr)
                .then().statusCode(200)
                .body("status", equalTo("completed"));
    }

    @Test
    void get_nonexistent_investigation_returns_404() {
        given().when().get("/api/layer9/investigations/" + UUID.randomUUID())
                .then().statusCode(404);
    }
}
