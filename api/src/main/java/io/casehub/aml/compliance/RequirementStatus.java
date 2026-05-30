package io.casehub.aml.compliance;

public enum RequirementStatus {
    /** Requirement demonstrably met with evidence. */
    CLOSED,
    /** Mechanism present but evidence incomplete. */
    PARTIAL,
    /** Mechanism present but obligation not met (e.g. SLA deadline passed). */
    BREACHED,
    /** Architectural gap; requirement not addressed. */
    GAP
}
