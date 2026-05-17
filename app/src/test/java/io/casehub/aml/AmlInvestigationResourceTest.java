package io.casehub.aml;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
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

    @Test
    void postInvestigation_validTransaction_returns200WithSummaryAndWorkItem() {
        given()
                .contentType(ContentType.JSON)
                .body(VALID_TX)
        .when()
                .post("/api/investigations")
        .then()
                .statusCode(200)
                .body("summary.transaction.id",        equalTo("TXN-001"))
                .body("summary.entityResolution.type",  equalTo("Completed"))
                .body("summary.patternAnalysis.type",   equalTo("Completed"))
                .body("summary.osintScreening",         notNullValue())
                .body("summary.sarNarrative",          notNullValue())
                .body("complianceReviewTaskId",        notNullValue());
    }

    @Test
    void postInvestigation_osintIsDeclined_notAnError() {
        // Layer 3: OSINT agent DECLINEs (insufficient clearance for PEP database).
        // The investigation completes — DECLINE is a formal scope boundary, not a failure.
        given()
                .contentType(ContentType.JSON)
                .body(VALID_TX)
        .when()
                .post("/api/investigations")
        .then()
                .statusCode(200)
                .body("summary.osintScreening.type",   equalTo("Declined"))
                .body("summary.osintScreening.reason", containsString("clearance"))
                .body("complianceReviewTaskId",        notNullValue());
    }

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
