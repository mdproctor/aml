package io.casehub.aml.engine;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AmlLayer5ResourceTest {

    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    /** Drain: wait for investigation to complete to prevent Quartz contamination. */
    private void drain(final String caseId) {
        await().atMost(DRAIN_TIMEOUT).pollInterval(POLL_INTERVAL).until(() ->
            "completed".equals(
                given().when().get("/api/layer6/investigations/" + caseId)
                        .then().extract().path("status")));
    }

    @Test
    void startInvestigation_returnsCaseId() {
        final var body = """
                {
                  "id": "TXN-L5-001-%s",
                  "originAccountId": "ACC-A",
                  "destinationAccountId": "ACC-B",
                  "amount": 50000,
                  "currency": "USD",
                  "timestamp": "2024-01-01T00:00:00Z",
                  "flagReason": "Unusual transaction pattern"
                }
                """.formatted(UUID.randomUUID());

        final var response = given()
                .contentType("application/json")
                .body(body)
                .when()
                .post("/api/layer5/investigations")
                .then()
                .statusCode(200)
                .extract()
                .as(Layer5InvestigationResponse.class);

        assertNotNull(response.caseId(), "caseId must be returned");
        assertNotNull(response.status(), "status must be returned");
        drain(response.caseId().toString());
    }

    @Test
    void startPepInvestigation_returnsCaseId() {
        final var body = """
                {
                  "id": "TXN-L5-PEP-%s",
                  "originAccountId": "ACC-PEP",
                  "destinationAccountId": "ACC-B",
                  "amount": 95000,
                  "currency": "USD",
                  "timestamp": "2024-01-01T00:00:00Z",
                  "flagReason": "PEP entity detected — high risk transfer"
                }
                """.formatted(UUID.randomUUID());

        final var response = given()
                .contentType("application/json")
                .body(body)
                .when()
                .post("/api/layer5/investigations")
                .then()
                .statusCode(200)
                .extract()
                .as(Layer5InvestigationResponse.class);

        assertNotNull(response.caseId(), "PEP investigation must return a case ID");
        drain(response.caseId().toString());
    }
}
