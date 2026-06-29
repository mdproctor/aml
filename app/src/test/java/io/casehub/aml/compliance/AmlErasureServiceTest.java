package io.casehub.aml.compliance;

import io.casehub.ledger.api.model.ErasureReason;
import io.casehub.ledger.runtime.privacy.LedgerErasureService;
import io.casehub.ledger.runtime.privacy.LedgerErasureService.ErasureResult;
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
        final AmlErasureService service = new AmlErasureService(ledger);

        final AmlErasureResult result = service.erase("officer-jane", ErasureReason.GDPR_ART_17_REQUEST);

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
        final AmlErasureService service = new AmlErasureService(ledger);

        final AmlErasureResult result = service.erase("officer-jane", ErasureReason.GDPR_ART_17_REQUEST);

        assertFalse(result.mappingFound());
        assertEquals(0L, result.affectedEntryCount());
        assertNull(result.receiptEntryId());
    }
}
