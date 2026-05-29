package io.casehub.aml.engine;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkerRoutingDecision(
        String capabilityTag,
        String selectedWorker,
        Double trustScore) {}   // null when Phase 0 (no trust history)
