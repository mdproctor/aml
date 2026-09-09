package io.casehub.aml.engine;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import java.util.UUID;

@QuarkusTest
class AmlLayer9AuthorizationTest {

    private static final String CASE_ID = UUID.randomUUID().toString();

    @Test
    @TestSecurity(user = "viewer-1", roles = "compliance-officers")
    void suspend_forbiddenWithoutComplianceRole() {
        RestAssured.given()
                .post("/api/layer9/investigations/" + CASE_ID + "/suspend")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "officer-1", roles = "aml-compliance")
    void suspend_allowedWithComplianceRole() {
        RestAssured.given()
                .post("/api/layer9/investigations/" + CASE_ID + "/suspend")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "mlro-1", roles = "aml-mlro")
    void suspend_allowedWithMlroRole() {
        RestAssured.given()
                .post("/api/layer9/investigations/" + CASE_ID + "/suspend")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "viewer-1", roles = "compliance-officers")
    void resume_forbiddenWithoutComplianceRole() {
        RestAssured.given()
                .post("/api/layer9/investigations/" + CASE_ID + "/resume")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "officer-1", roles = "aml-compliance")
    void resume_allowedWithComplianceRole() {
        RestAssured.given()
                .post("/api/layer9/investigations/" + CASE_ID + "/resume")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "viewer-1", roles = "compliance-officers")
    void getInvestigation_allowedWithAnyRole() {
        RestAssured.given()
                .get("/api/layer9/investigations/" + CASE_ID)
                .then()
                .statusCode(404);
    }
}
