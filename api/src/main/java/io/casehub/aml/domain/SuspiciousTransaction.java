package io.casehub.aml.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record SuspiciousTransaction(
        String id,
        String originAccountId,
        String destinationAccountId,
        BigDecimal amount,
        String currency,
        Instant timestamp,
        FlagReason flagReason) {
}
