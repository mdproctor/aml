package io.casehub.aml.compliance;

import java.util.UUID;

public record ActorErasureResult(
        String erasedActorId,
        boolean mappingFound,
        long affectedEntryCount,
        UUID receiptEntryId) {}
