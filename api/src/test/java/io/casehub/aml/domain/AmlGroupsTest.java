package io.casehub.aml.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AmlGroupsTest {

    @Test
    void allConstants_nonNullAndNonBlank() {
        assertNotNull(AmlGroups.MLRO);
        assertFalse(AmlGroups.MLRO.isBlank());
        assertNotNull(AmlGroups.AML_COMPLIANCE);
        assertFalse(AmlGroups.AML_COMPLIANCE.isBlank());
        assertNotNull(AmlGroups.AML_SENIOR_COMPLIANCE);
        assertFalse(AmlGroups.AML_SENIOR_COMPLIANCE.isBlank());
    }

    @Test
    void allConstants_distinct() {
        assertNotEquals(AmlGroups.MLRO, AmlGroups.AML_COMPLIANCE);
        assertNotEquals(AmlGroups.MLRO, AmlGroups.AML_SENIOR_COMPLIANCE);
        assertNotEquals(AmlGroups.AML_COMPLIANCE, AmlGroups.AML_SENIOR_COMPLIANCE);
    }

    @Test
    void complianceOfficersConstantExists() {
        assertNotNull(AmlGroups.COMPLIANCE_OFFICERS);
        assertFalse(AmlGroups.COMPLIANCE_OFFICERS.isBlank());
        assertEquals("compliance-officers", AmlGroups.COMPLIANCE_OFFICERS);
    }
}
