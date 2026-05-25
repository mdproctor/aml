package io.casehub.aml.engine;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.Instant;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AmlLayer5ResourceTest {

    @Test
    void startInvestigation_returnsCaseId() {
        final var body = """
                {
                  "id": "TXN-L5-001",
                  "originAccountId": "ACC-A",
                  "destinationAccountId": "ACC-B",
                  "amount": 50000,
                  "currency": "USD",
                  "timestamp": "2024-01-01T00:00:00Z",
                  "flagReason": "Unusual transaction pattern"
                }
                """;

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
    }

    @Test
    void startPepInvestigation_returnsCaseId() {
        final var body = """
                {
                  "id": "TXN-L5-PEP",
                  "originAccountId": "ACC-PEP",
                  "destinationAccountId": "ACC-B",
                  "amount": 95000,
                  "currency": "USD",
                  "timestamp": "2024-01-01T00:00:00Z",
                  "flagReason": "PEP entity detected — high risk transfer"
                }
                """;

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
    }
}
