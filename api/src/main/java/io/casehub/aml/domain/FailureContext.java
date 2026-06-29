package io.casehub.aml.domain;

import java.time.Instant;
import java.util.List;

public record FailureContext(
        String triggerGoalName,
        String triggerGoalKind,
        List<FailureEvent> failureEvents,
        Instant occurredAt) {}
