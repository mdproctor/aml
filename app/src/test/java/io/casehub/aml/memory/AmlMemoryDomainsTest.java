package io.casehub.aml.memory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AmlMemoryDomainsTest {
    @Test void entityRiskDomainName() { assertEquals("aml.entity-risk", AmlMemoryDomains.ENTITY_RISK.name()); }
    @Test void networkDomainName()    { assertEquals("aml.network",      AmlMemoryDomains.NETWORK.name()); }
    @Test void patternDomainName()    { assertEquals("aml.pattern",       AmlMemoryDomains.PATTERN.name()); }
    @Test void domainsAreDistinct() {
        assertNotEquals(AmlMemoryDomains.ENTITY_RISK, AmlMemoryDomains.NETWORK);
        assertNotEquals(AmlMemoryDomains.NETWORK, AmlMemoryDomains.PATTERN);
        assertNotEquals(AmlMemoryDomains.ENTITY_RISK, AmlMemoryDomains.PATTERN);
    }
}
