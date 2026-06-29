package io.casehub.aml.domain;

import java.time.Instant;

public record FailureEvent(
        String eventType,
        String workerId,
        Instant timestamp,
        String detail) {}
