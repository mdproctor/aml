package io.casehub.aml.memory;

import io.casehub.aml.domain.SarOutcome;
import io.casehub.aml.domain.SarVerdict;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.engine.AmlEngineCoordinator;
import io.casehub.aml.engine.SarOutcomeRecordedEvent;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.memory.CaseMemoryStore;
import io.casehub.platform.api.memory.MemoryAttributeKeys;
import io.casehub.platform.api.memory.MemoryQuery;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AmlSarOutcomeObserverTest {

    @Inject AmlEngineCoordinator coordinator;
    @Inject Event<SarOutcomeRecordedEvent> sarOutcomeEvent;
    @Inject CaseMemoryStore memoryStore;

    private static final String TENANT = TenancyConstants.DEFAULT_TENANT_ID;

    @Test
    void sarOutcomeEvent_writesMemoryForBothAccounts() {
        SuspiciousTransaction tx = new SuspiciousTransaction(
            "TXN-SAR-MEM-001-" + UUID.randomUUID(), "ACC-SAR-ORIGIN-1-" + UUID.randomUUID(), "ACC-SAR-DEST-1-" + UUID.randomUUID(),
            new BigDecimal("80000"), "USD", Instant.now(), "SAR outcome test");
        UUID caseId = coordinator.startInvestigation(tx);

        sarOutcomeEvent.fire(new SarOutcomeRecordedEvent(
            caseId, new SarOutcome(SarVerdict.UPHELD, "SAR upheld", 0.92)));

        List<io.casehub.platform.api.memory.Memory> originMemories = memoryStore.query(
            MemoryQuery.forEntities(List.of(tx.originAccountId()), AmlMemoryDomains.ENTITY_RISK, TENANT));
        List<io.casehub.platform.api.memory.Memory> destMemories = memoryStore.query(
            MemoryQuery.forEntities(List.of(tx.destinationAccountId()), AmlMemoryDomains.ENTITY_RISK, TENANT));

        assertFalse(originMemories.isEmpty(), "Origin account must have SAR outcome memory");
        assertFalse(destMemories.isEmpty(), "Destination account must have SAR outcome memory");
        assertTrue(originMemories.stream().anyMatch(m -> m.text().contains("UPHELD")));
    }

    @Test
    void withdrawn_sarOutcome_writesZeroConfidenceReversal() {
        SuspiciousTransaction tx = new SuspiciousTransaction(
            "TXN-SAR-MEM-002-" + UUID.randomUUID(), "ACC-SAR-WITHDRAWN-" + UUID.randomUUID(), "ACC-SAR-WITHDRAWN-DEST-" + UUID.randomUUID(),
            new BigDecimal("30000"), "USD", Instant.now(), "SAR withdrawn test");
        UUID caseId = coordinator.startInvestigation(tx);

        sarOutcomeEvent.fire(new SarOutcomeRecordedEvent(
            caseId, new SarOutcome(SarVerdict.WITHDRAWN, "SAR withdrawn", 0.10)));

        List<io.casehub.platform.api.memory.Memory> memories = memoryStore.query(
            MemoryQuery.forEntities(List.of(tx.originAccountId()), AmlMemoryDomains.ENTITY_RISK, TENANT));

        assertFalse(memories.isEmpty(), "WITHDRAWN must still write a reversal memory");
        String confidence = memories.get(0).attributes().get(MemoryAttributeKeys.CONFIDENCE);
        assertEquals("0.0000", confidence, "WITHDRAWN verdict must write confidence=0.0");
    }

    @Test
    void sarOutcomeEvent_withNoLedgerEntry_doesNotThrow() {
        UUID nonexistentCaseId = UUID.randomUUID();
        assertDoesNotThrow(() ->
            sarOutcomeEvent.fire(new SarOutcomeRecordedEvent(
                nonexistentCaseId, new SarOutcome(SarVerdict.UPHELD, "test", 0.9))));
    }
}
