package io.casehub.aml.domain;

public enum InvestigationStatus {
    IN_PROGRESS,
    COMPLETED;

    public String toWireFormat() {
        return name().toLowerCase().replace('_', '-');
    }

    public static InvestigationStatus fromWireFormat(String value) {
        return valueOf(value.toUpperCase().replace('-', '_'));
    }
}
