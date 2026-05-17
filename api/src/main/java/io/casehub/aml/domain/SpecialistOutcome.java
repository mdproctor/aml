package io.casehub.aml.domain;

public sealed interface SpecialistOutcome<T>
        permits SpecialistOutcome.Completed, SpecialistOutcome.Declined, SpecialistOutcome.Failed {

    record Completed<T>(T result) implements SpecialistOutcome<T> {}

    record Declined<T>(String agentId, String capability, String reason) implements SpecialistOutcome<T> {}

    record Failed<T>(String agentId, String capability, String reason) implements SpecialistOutcome<T> {}
}
