package io.casehub.aml.domain;

public record OsintResult(
        boolean sanctionsHit,
        boolean pepHit,
        boolean declined,
        String reason) {
    public OsintResult {
        if (declined && (sanctionsHit || pepHit)) {
            throw new IllegalArgumentException(
                    "declined screening cannot report sanctions or PEP hits");
        }
    }
}
