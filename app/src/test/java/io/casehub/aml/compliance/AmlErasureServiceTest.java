package io.casehub.aml.compliance;

import io.casehub.aml.ledger.AmlLedgerService;
import io.casehub.ledger.api.model.ErasureReason;
import io.casehub.ledger.runtime.privacy.LedgerErasureService;
import io.casehub.ledger.runtime.privacy.LedgerErasureService.ErasureResult;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.neocortex.memory.CaseMemoryStore;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class AmlErasureServiceTest {

    @Test
    void erase_maps_ledger_result_with_receipt() {
        final UUID receiptId = UUID.randomUUID();
        final LedgerErasureService ledger = Mockito.mock(LedgerErasureService.class);
        when(ledger.erase(eq("officer-jane"), eq(ErasureReason.GDPR_ART_17_REQUEST)))
                .thenReturn(new ErasureResult("officer-jane", true, 5L, Optional.of(receiptId)));
        final AmlErasureService service = new AmlErasureService(
                ledger, Mockito.mock(CaseMemoryStore.class),
                mockPrincipal("x", ActorType.SYSTEM), AmlLedgerService.noOp());

        final ActorErasureResult result = service.erase("officer-jane", ErasureReason.GDPR_ART_17_REQUEST);

        assertEquals("officer-jane", result.erasedActorId());
        assertTrue(result.mappingFound());
        assertEquals(5L, result.affectedEntryCount());
        assertEquals(receiptId, result.receiptEntryId());
    }

    @Test
    void erase_maps_empty_receipt_to_null() {
        final LedgerErasureService ledger = Mockito.mock(LedgerErasureService.class);
        when(ledger.erase(anyString(), any()))
                .thenReturn(new ErasureResult("officer-jane", false, 0L, Optional.empty()));
        final AmlErasureService service = new AmlErasureService(
                ledger, Mockito.mock(CaseMemoryStore.class),
                mockPrincipal("x", ActorType.SYSTEM), AmlLedgerService.noOp());

        final ActorErasureResult result = service.erase("officer-jane", ErasureReason.GDPR_ART_17_REQUEST);

        assertFalse(result.mappingFound());
        assertEquals(0L, result.affectedEntryCount());
        assertNull(result.receiptEntryId());
    }

    @Test
    void eraseEntity_returns_count_and_receipt() {
        final UUID receiptId = UUID.randomUUID();
        final LedgerErasureService ledger = Mockito.mock(LedgerErasureService.class);
        final CaseMemoryStore memoryStore = Mockito.mock(CaseMemoryStore.class);
        final CurrentPrincipal principal = mockPrincipal("officer-1", ActorType.HUMAN);
        final AmlLedgerService ledgerService = AmlLedgerService.stub(receiptId);

        when(memoryStore.eraseEntity(eq("ACCT-12345"), anyString())).thenReturn(3);

        final AmlErasureService service = new AmlErasureService(
                ledger, memoryStore, principal, ledgerService);
        final EntityErasureResult result = service.eraseEntity(
                "ACCT-12345", ErasureReason.GDPR_ART_17_REQUEST);

        assertEquals("ACCT-12345", result.entityId());
        assertEquals(3, result.memoriesErased());
        assertEquals(receiptId, result.receiptEntryId());
    }

    @Test
    void eraseEntity_with_no_memories_still_writes_receipt() {
        final UUID receiptId = UUID.randomUUID();
        final LedgerErasureService ledger = Mockito.mock(LedgerErasureService.class);
        final CaseMemoryStore memoryStore = Mockito.mock(CaseMemoryStore.class);
        final CurrentPrincipal principal = mockPrincipal("officer-1", ActorType.HUMAN);
        final AmlLedgerService ledgerService = AmlLedgerService.stub(receiptId);

        when(memoryStore.eraseEntity(eq("ACCT-99999"), anyString())).thenReturn(0);

        final AmlErasureService service = new AmlErasureService(
                ledger, memoryStore, principal, ledgerService);
        final EntityErasureResult result = service.eraseEntity(
                "ACCT-99999", ErasureReason.GDPR_ART_17_REQUEST);

        assertEquals(0, result.memoriesErased());
        assertEquals(receiptId, result.receiptEntryId());
    }

    private static CurrentPrincipal mockPrincipal(String actorId, ActorType actorType) {
        final CurrentPrincipal p = Mockito.mock(CurrentPrincipal.class);
        when(p.actorId()).thenReturn(actorId);
        when(p.actorType()).thenReturn(actorType);
        when(p.tenancyId()).thenReturn("default");
        return p;
    }
}
