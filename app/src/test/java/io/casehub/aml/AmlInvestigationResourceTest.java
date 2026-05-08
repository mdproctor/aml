package io.casehub.aml;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AmlInvestigationResourceTest {

    private static final String VALID_TX = """
            {
              "id": "TXN-001",
              "originAccountId": "ACC-A",
              "destinationAccountId": "ACC-B",
              "amount": 100000,
              "currency": "USD",
              "timestamp": "2024-03-15T10:00:00Z",
              "flagReason": "Structuring"
            }
            """;

    // Happy path: valid transaction returns 200 with all summary fields present
    @Test
    void postInvestigation_validTransaction_returns200WithSummary() {
        given()
                .contentType(ContentType.JSON)
                .body(VALID_TX)
        .when()
                .post("/api/investigations")
        .then()
                .statusCode(200)
                .body("transaction.id",   equalTo("TXN-001"))
                .body("entityResolution", notNullValue())
                .body("patternAnalysis",  notNullValue())
                .body("osintScreening",   notNullValue())
                .body("sarNarrative",     notNullValue());
    }

    // Robustness: malformed JSON body is rejected with 400
    @Test
    void postInvestigation_malformedJson_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("not-valid-json")
        .when()
                .post("/api/investigations")
        .then()
                .statusCode(400);
    }
}
