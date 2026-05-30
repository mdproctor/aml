package io.casehub.aml.compliance;

public record GdprErasureRequirement(
    String id,
    String citation,
    String mechanism,
    boolean erasureCapabilityWired,
    boolean pseudonymizationActive,
    String erasureEndpoint
) {
    public static final String REQUIREMENT_ID = "GDPR-ART17-ERASURE";
    public static final String CITATION =
        "GDPR Art. 17 / FATF privacy obligation — PII erasure preserving audit structure";
    public static final String MECHANISM =
        "LedgerErasureService pseudonymizes actorId in ledger_entry rows via ActorIdentity token. " +
        "Audit entries remain intact; actor identity is replaced with an opaque token. " +
        "NOTE: current tutorial erases system actor; proper data subject erasure requires " +
        "AML_SAR_OFFICER_REVIEWED ledger event — tracked as casehubio/aml#44.";
    public static final String ERASURE_ENDPOINT = "POST /api/actors/{actorId}/erasure";
}
