package io.casehub.aml.memory;

import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.EraseRequest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AmlMemoryRoundtripTest {

    @Inject AmlMemoryService memoryService;
    @Inject CaseMemoryStore memoryStore;

    private static final String TENANT = TenancyConstants.DEFAULT_TENANT_ID;

    private SuspiciousTransaction tx(String id, String origin, String dest) {
        return new SuspiciousTransaction(id, origin, dest,
            new BigDecimal("50000"), "USD", Instant.now(), "Structuring");
    }

    @Test
    void storeEntityRisk_thenQueryPriorContext_returnsStoredFact() {
        SuspiciousTransaction transaction = tx("TXN-RT-001", "ACC-RT-ORIGIN-1", "ACC-RT-DEST-1");
        EntityResolutionResult result = new EntityResolutionResult(
            "ACC-RT-ORIGIN-1", "direct-owner", "PEP", 0.92);

        memoryService.storeEntityRisk(null, "ACC-RT-ORIGIN-1", result);

        AmlPriorContext ctx = memoryService.queryPriorContext(transaction);
        assertFalse(ctx.entityRisk().isEmpty(),
            "queryPriorContext must return the stored entity-risk memory");
        assertTrue(ctx.entityRisk().stream()
            .anyMatch(m -> "ACC-RT-ORIGIN-1".equals(m.entityId())));
        assertTrue(ctx.isKnownHighRisk(),
            "PEP entity with confidence 0.92 must trigger isKnownHighRisk");
    }

    @Test
    void storeNetworkRelationship_bothAccountIdsQueryable() {
        SuspiciousTransaction transaction = tx("TXN-RT-002", "ACC-RT-NET-A", "ACC-RT-NET-B");
        EntityResolutionResult result = new EntityResolutionResult(
            "entity-stub", "direct-owner", "CORPORATE", 0.35);

        memoryService.storeNetworkRelationship(null, transaction, result);

        AmlPriorContext ctxA = memoryService.queryPriorContext(
            tx("TXN-RT-002-A", "ACC-RT-NET-A", "unrelated"));
        assertFalse(ctxA.network().isEmpty(), "origin account must have network memory");

        AmlPriorContext ctxB = memoryService.queryPriorContext(
            tx("TXN-RT-002-B", "ACC-RT-NET-B", "unrelated"));
        assertFalse(ctxB.network().isEmpty(), "destination account must have network memory");
    }

    @Test
    void storePatternFindings_bothAccountIdsQueryable() {
        SuspiciousTransaction transaction = tx("TXN-RT-003", "ACC-RT-PAT-A", "ACC-RT-PAT-B");
        PatternAnalysisResult result = new PatternAnalysisResult(true, "Smurfing detected");

        memoryService.storePatternFindings(null, transaction, result);

        AmlPriorContext ctxA = memoryService.queryPriorContext(
            tx("TXN-RT-003-A", "ACC-RT-PAT-A", "unrelated"));
        assertFalse(ctxA.pattern().isEmpty(), "origin account must have pattern memory");

        AmlPriorContext ctxB = memoryService.queryPriorContext(
            tx("TXN-RT-003-B", "ACC-RT-PAT-B", "unrelated"));
        assertFalse(ctxB.pattern().isEmpty(), "destination account must have pattern memory");
    }

    @Test
    void erasure_removesEntityRiskForAccount_leavesNetworkIntact() {
        SuspiciousTransaction transaction = tx("TXN-RT-004", "ACC-RT-ERASE-A", "ACC-RT-ERASE-B");
        EntityResolutionResult result = new EntityResolutionResult(
            "ACC-RT-ERASE-A", "chain", "CORPORATE", 0.85);

        memoryService.storeEntityRisk(null, "ACC-RT-ERASE-A", result);
        memoryService.storeNetworkRelationship(null, transaction, result);

        AmlPriorContext before = memoryService.queryPriorContext(transaction);
        assertFalse(before.entityRisk().isEmpty(), "must have entity-risk before erase");
        assertFalse(before.network().isEmpty(), "must have network before erase");

        // Erase entity-risk domain only (caseId null = erase all for entity+domain)
        memoryStore.erase(new EraseRequest("ACC-RT-ERASE-A", AmlMemoryDomains.ENTITY_RISK, TENANT, null));

        AmlPriorContext after = memoryService.queryPriorContext(transaction);
        assertTrue(after.entityRisk().isEmpty(), "entity-risk must be empty after erase");
        assertFalse(after.network().isEmpty(), "network domain must be unaffected");
    }

    @Test
    void hasHistory_false_whenNoMemoriesExist() {
        SuspiciousTransaction transaction = tx("TXN-RT-005", "ACC-RT-EMPTY-A", "ACC-RT-EMPTY-B");
        AmlPriorContext ctx = memoryService.queryPriorContext(transaction);
        assertFalse(ctx.hasHistory());
        assertFalse(ctx.isKnownHighRisk());
    }
}
