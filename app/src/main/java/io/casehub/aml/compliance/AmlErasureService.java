package io.casehub.aml.compliance;

import io.casehub.ledger.api.model.ErasureReason;
import io.casehub.ledger.runtime.privacy.LedgerErasureService;
import io.casehub.ledger.runtime.privacy.LedgerErasureService.ErasureResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AmlErasureService {

    private final LedgerErasureService ledgerErasureService;

    @Inject
    public AmlErasureService(final LedgerErasureService ledgerErasureService) {
        this.ledgerErasureService = ledgerErasureService;
    }

    public AmlErasureResult erase(final String actorId, final ErasureReason reason) {
        final ErasureResult ledgerResult = ledgerErasureService.erase(actorId, reason);
        return new AmlErasureResult(
                ledgerResult.rawActorId(),
                ledgerResult.mappingFound(),
                ledgerResult.affectedEntryCount(),
                ledgerResult.receiptEntryId().orElse(null));
    }
}
