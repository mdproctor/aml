package io.casehub.aml.compliance;

import java.util.UUID;

public record EntityErasureResult(
        String entityId,
        int memoriesErased,
        UUID receiptEntryId) {}
