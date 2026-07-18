package io.casehub.aml.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CbrEnumsTest {

    @Test
    void entityType_hasFourValues() {
        assertEquals(4, EntityType.values().length);
        assertNotNull(EntityType.valueOf("INDIVIDUAL"));
        assertNotNull(EntityType.valueOf("CORPORATE"));
        assertNotNull(EntityType.valueOf("SHELL_COMPANY"));
        assertNotNull(EntityType.valueOf("PEP"));
    }

    @Test
    void jurisdictionRisk_hasThreeValues() {
        assertEquals(3, JurisdictionRisk.values().length);
        assertNotNull(JurisdictionRisk.valueOf("HIGH"));
        assertNotNull(JurisdictionRisk.valueOf("MEDIUM"));
        assertNotNull(JurisdictionRisk.valueOf("LOW"));
    }

    @Test
    void networkComplexity_hasThreeValues() {
        assertEquals(3, NetworkComplexity.values().length);
        assertNotNull(NetworkComplexity.valueOf("SINGLE_ENTITY"));
        assertNotNull(NetworkComplexity.valueOf("SMALL_NETWORK"));
        assertNotNull(NetworkComplexity.valueOf("LARGE_NETWORK"));
    }
}
