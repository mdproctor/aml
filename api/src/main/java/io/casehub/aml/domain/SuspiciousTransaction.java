package io.casehub.aml.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A flagged financial transaction that opens an AML investigation case.
 *
 * <p>Layer 1 uses this as a plain record with no persistence or coordination.
 * Later layers add persistence (Layer 2), ledger audit (Layer 4), and
 * engine-driven adaptive investigation paths (Layer 5).
 */
public record SuspiciousTransaction(
        String id,
        String originAccountId,
        String destinationAccountId,
        BigDecimal amount,
        String currency,
        Instant timestamp,
        String flagReason) {
}
