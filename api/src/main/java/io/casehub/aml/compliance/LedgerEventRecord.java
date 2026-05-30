package io.casehub.aml.compliance;

import java.time.Instant;
import java.util.UUID;

public record LedgerEventRecord(
    UUID entryId,
    String eventType,
    String actorId,
    String actorRole,
    Instant occurredAt,
    UUID causedByEntryId,
    String digest,
    AmlInclusionProof inclusionProof
) {}
