package io.casehub.aml.domain;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Typed taxonomy of consequential AML actions that workers may declare as {@code PlannedAction}
 * before the engine advances the case. Each constant encodes its regulatory gate policy,
 * reversibility, candidate approver groups, reason string, and oversight scope.
 *
 * <p>Classification logic lives in {@code AmlActionRiskClassifier}. This enum owns only the
 * data — keeping it pure Java with no framework dependencies so both api and app modules can use it.
 *
 * <p>candidateGroups semantics (GE-20260607-326c7e): fewer entries = more restrictive in the
 * engine chain. SAR_FILING with ["aml-mlro"] (1 group) is the tightest gate in the system.
 */
public enum AmlActionType {

    SAR_FILING(
        GatePolicy.ALWAYS, false,
        List.of(AmlGroups.MLRO),
        "SAR submission to regulator — MLRO sign-off required (FinCEN/FCA)"),

    ACCOUNT_RESTRICTION(
        GatePolicy.RISK_SCORE_THRESHOLD, true,
        List.of(AmlGroups.AML_COMPLIANCE),
        "Account restriction affects customer — confirm before action"),

    TRANSACTION_BLOCKING(
        GatePolicy.CONFIDENCE_THRESHOLD, false,
        List.of(AmlGroups.AML_COMPLIANCE),
        "Transaction block — low-confidence pattern — human review required"),

    ENTITY_LINK_CREATION(
        GatePolicy.RISK_SCORE_THRESHOLD, true,
        List.of(AmlGroups.AML_COMPLIANCE),
        "Entity network link has downstream investigation implications — confirm evidence basis"),

    LAW_ENFORCEMENT_REFERRAL(
        GatePolicy.ALWAYS, false,
        List.of(AmlGroups.AML_SENIOR_COMPLIANCE),
        "Law enforcement referral — senior compliance director approval required"),

    INVESTIGATION_CLEARANCE(
        GatePolicy.ALWAYS, true,
        List.of(AmlGroups.AML_COMPLIANCE),
        "Investigation clearance on inconclusive evidence — compliance review required");

    /** Gate policy variants — determines how the classifier evaluates context. */
    public enum GatePolicy {
        ALWAYS,              // unconditional gate regardless of context
        RISK_SCORE_THRESHOLD, // gate when context["riskScore"] >= 0.8 OR entityType == "PEP"
        CONFIDENCE_THRESHOLD  // gate when context["confidenceScore"] < 0.9 (low confidence)
    }

    private static final String OVERSIGHT_SCOPE = "casehubio/aml/oversight";

    private final GatePolicy gatePolicy;
    private final boolean reversible;
    private final List<String> candidateGroups;
    private final String reason;

    AmlActionType(
            final GatePolicy gatePolicy,
            final boolean reversible,
            final List<String> candidateGroups,
            final String reason) {
        this.gatePolicy = gatePolicy;
        this.reversible = reversible;
        this.candidateGroups = candidateGroups;
        this.reason = reason;
    }

    public GatePolicy gatePolicy() { return gatePolicy; }
    public boolean reversible() { return reversible; }
    public List<String> candidateGroups() { return candidateGroups; }
    public String reason() { return reason; }
    public String scope() { return OVERSIGHT_SCOPE; }

    /** expiresIn is null — expiry policy is regulatory and configurable post-GA. */
    public Duration expiresIn() { return null; }

    /** Returns the PlannedAction actionType string: e.g. {@code SAR_FILING → "sar.filing"}. */
    public String actionType() {
        return name().toLowerCase().replace('_', '.');
    }

    /**
     * Parses a {@code PlannedAction.actionType()} string back to the enum constant.
     * Uses stream filter — never throws on unrecognised or null input.
     */
    public static Optional<AmlActionType> fromActionType(final String actionType) {
        if (actionType == null) return Optional.empty();
        return Arrays.stream(values())
            .filter(a -> a.actionType().equals(actionType))
            .findFirst();
    }
}
