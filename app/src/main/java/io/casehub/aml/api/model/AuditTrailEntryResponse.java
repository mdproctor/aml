package io.casehub.aml.api.model;

import java.time.Instant;
import java.util.UUID;

/**
 * REST response record for ledger entries in the audit trail.
 * Maps from {@link io.casehub.ledger.api.model.LedgerEntry} to a JSON-friendly format.
 */
public record AuditTrailEntryResponse(
        UUID entryId,
        String entryType,
        String actorId,
        String actorRole,
        Instant occurredAt,
        UUID causedByEntryId,
        String digest,
        int sequenceNumber
) {}
