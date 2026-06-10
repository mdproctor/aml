package io.casehub.aml.routing;

import io.casehub.api.spi.PlannedAction;
import io.casehub.api.spi.RiskDecision;
import io.casehub.aml.domain.AmlActionType;
import io.casehub.aml.domain.AmlGroups;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AmlActionRiskClassifierTest {

    AmlActionRiskClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new AmlActionRiskClassifier();
    }

    // ── ALWAYS-gated types ───────────────────────────────────────────────────

    @Test
    void sarFiling_alwaysGatesRegardlessOfContext() {
        final RiskDecision result = classify(AmlActionType.SAR_FILING, Map.of("transactionId", "TXN-001"));
        assertGateRequired(result, AmlGroups.MLRO, false);
    }

    @Test
    void sarFiling_gatesWithEmptyContext() {
        final RiskDecision result = classify(AmlActionType.SAR_FILING, Map.of());
        assertGateRequired(result, AmlGroups.MLRO, false);
    }

    @Test
    void lawEnforcementReferral_alwaysGates() {
        final RiskDecision result = classify(AmlActionType.LAW_ENFORCEMENT_REFERRAL, Map.of());
        assertGateRequired(result, AmlGroups.AML_SENIOR_COMPLIANCE, false);
    }

    // ── ENTITY_LINK_CREATION (RISK_SCORE_THRESHOLD) ──────────────────────────

    @Test
    void entityLink_pep_alwaysGatesRegardlessOfRiskScore() {
        final RiskDecision result = classify(AmlActionType.ENTITY_LINK_CREATION,
            Map.of("entityType", "PEP", "riskScore", 0.2));
        assertGateRequired(result, AmlGroups.AML_COMPLIANCE, true);
    }

    @Test
    void entityLink_highRisk_gates() {
        final RiskDecision result = classify(AmlActionType.ENTITY_LINK_CREATION,
            Map.of("entityType", "CORPORATE", "riskScore", 0.9));
        assertGateRequired(result, AmlGroups.AML_COMPLIANCE, true);
    }

    @Test
    void entityLink_atThreshold_gates() {
        final RiskDecision result = classify(AmlActionType.ENTITY_LINK_CREATION,
            Map.of("entityType", "CORPORATE", "riskScore", 0.8));
        assertGateRequired(result, AmlGroups.AML_COMPLIANCE, true);
    }

    @Test
    void entityLink_lowRiskCorporate_autonomous() {
        final RiskDecision result = classify(AmlActionType.ENTITY_LINK_CREATION,
            Map.of("entityType", "CORPORATE", "riskScore", 0.35));
        assertInstanceOf(RiskDecision.Autonomous.class, result,
            "Low-risk CORPORATE entity link must be Autonomous");
    }

    @Test
    void entityLink_justBelowThreshold_autonomous() {
        final RiskDecision result = classify(AmlActionType.ENTITY_LINK_CREATION,
            Map.of("entityType", "CORPORATE", "riskScore", 0.799));
        assertInstanceOf(RiskDecision.Autonomous.class, result);
    }

    @Test
    void entityLink_missingRiskScore_failClosed() {
        final RiskDecision result = classify(AmlActionType.ENTITY_LINK_CREATION,
            Map.of("entityType", "CORPORATE"));
        assertGateRequiredWithReason(result, "Risk assessment unavailable");
    }

    @Test
    void entityLink_emptyContext_failClosed() {
        final RiskDecision result = classify(AmlActionType.ENTITY_LINK_CREATION, Map.of());
        assertGateRequiredWithReason(result, "Risk assessment unavailable");
    }

    @Test
    void entityLink_nullContext_failClosed() {
        final RiskDecision result = classifier.classify(
            PlannedAction.of("desc", AmlActionType.ENTITY_LINK_CREATION.actionType(), null));
        assertGateRequiredWithReason(result, "Risk assessment unavailable");
    }

    // ── ACCOUNT_RESTRICTION (RISK_SCORE_THRESHOLD) ───────────────────────────

    @Test
    void accountRestriction_highRiskScore_gates() {
        final RiskDecision result = classify(AmlActionType.ACCOUNT_RESTRICTION,
            Map.of("entityType", "CORPORATE", "riskScore", 0.85));
        assertGateRequired(result, AmlGroups.AML_COMPLIANCE, true);
    }

    @Test
    void accountRestriction_pepEntity_gates() {
        final RiskDecision result = classify(AmlActionType.ACCOUNT_RESTRICTION,
            Map.of("entityType", "PEP", "riskScore", 0.2));
        assertGateRequired(result, AmlGroups.AML_COMPLIANCE, true);
    }

    @Test
    void accountRestriction_lowRiskCorporate_autonomous() {
        final RiskDecision result = classify(AmlActionType.ACCOUNT_RESTRICTION,
            Map.of("entityType", "CORPORATE", "riskScore", 0.5));
        assertInstanceOf(RiskDecision.Autonomous.class, result);
    }

    @Test
    void accountRestriction_missingRiskScore_failClosed() {
        final RiskDecision result = classify(AmlActionType.ACCOUNT_RESTRICTION,
            Map.of("entityType", "CORPORATE"));
        assertGateRequiredWithReason(result, "Risk assessment unavailable");
    }

    // ── TRANSACTION_BLOCKING (CONFIDENCE_THRESHOLD) ──────────────────────────

    @Test
    void transactionBlocking_highConfidence_autonomous() {
        final RiskDecision result = classify(AmlActionType.TRANSACTION_BLOCKING,
            Map.of("confidenceScore", 0.95));
        assertInstanceOf(RiskDecision.Autonomous.class, result,
            "High confidence must proceed autonomously");
    }

    @Test
    void transactionBlocking_atThreshold_autonomous() {
        final RiskDecision result = classify(AmlActionType.TRANSACTION_BLOCKING,
            Map.of("confidenceScore", 0.9));
        assertInstanceOf(RiskDecision.Autonomous.class, result);
    }

    @Test
    void transactionBlocking_belowThreshold_gates() {
        final RiskDecision result = classify(AmlActionType.TRANSACTION_BLOCKING,
            Map.of("confidenceScore", 0.7));
        assertGateRequired(result, AmlGroups.AML_COMPLIANCE, false);
    }

    @Test
    void transactionBlocking_justBelowThreshold_gates() {
        final RiskDecision result = classify(AmlActionType.TRANSACTION_BLOCKING,
            Map.of("confidenceScore", 0.899));
        assertGateRequired(result, AmlGroups.AML_COMPLIANCE, false);
    }

    @Test
    void transactionBlocking_missingConfidenceScore_failClosed() {
        final RiskDecision result = classify(AmlActionType.TRANSACTION_BLOCKING, Map.of());
        assertGateRequiredWithReason(result, "Risk assessment unavailable");
    }

    // ── Unknown / null actionType ─────────────────────────────────────────────

    @Test
    void unknownActionType_autonomous() {
        final RiskDecision result = classifier.classify(
            PlannedAction.of("something", "foo.bar", Map.of()));
        assertInstanceOf(RiskDecision.Autonomous.class, result,
            "Unknown action type must be Autonomous");
    }

    @Test
    void nullActionType_autonomous() {
        final RiskDecision result = classifier.classify(
            PlannedAction.of("something", null, Map.of()));
        assertInstanceOf(RiskDecision.Autonomous.class, result);
    }

    // ── Gate properties ───────────────────────────────────────────────────────

    @Test
    void gateRequired_scopeIsAmlOversight() {
        final RiskDecision result = classify(AmlActionType.SAR_FILING, Map.of());
        final RiskDecision.GateRequired gate = assertInstanceOf(RiskDecision.GateRequired.class, result);
        assertEquals("casehubio/aml/oversight", gate.scope());
    }

    @Test
    void gateRequired_expiresInIsNull() {
        final RiskDecision result = classify(AmlActionType.SAR_FILING, Map.of());
        final RiskDecision.GateRequired gate = assertInstanceOf(RiskDecision.GateRequired.class, result);
        assertNull(gate.expiresIn());
    }

    @Test
    void gateRequired_missingContext_usesTypeGroups() {
        final RiskDecision result = classify(AmlActionType.ENTITY_LINK_CREATION, Map.of());
        final RiskDecision.GateRequired gate = assertInstanceOf(RiskDecision.GateRequired.class, result);
        assertEquals(AmlActionType.ENTITY_LINK_CREATION.candidateGroups(), gate.candidateGroups());
    }

    @Test
    void gateRequired_missingContext_preservesReversible() {
        // SAR_FILING is reversible=false — missingContext must not override to true
        final RiskDecision result = classify(AmlActionType.SAR_FILING, Map.of());
        final RiskDecision.GateRequired gate = assertInstanceOf(RiskDecision.GateRequired.class, result);
        assertFalse(gate.reversible(), "SAR_FILING missing-context gate must be reversible=false");

        // ENTITY_LINK_CREATION is reversible=true — missingContext must preserve that
        final RiskDecision result2 = classify(AmlActionType.ENTITY_LINK_CREATION, Map.of());
        final RiskDecision.GateRequired gate2 = assertInstanceOf(RiskDecision.GateRequired.class, result2);
        assertTrue(gate2.reversible(), "ENTITY_LINK_CREATION missing-context gate must be reversible=true");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RiskDecision classify(final AmlActionType type, final Map<String, Object> context) {
        return classifier.classify(PlannedAction.of("test action", type.actionType(), context));
    }

    private void assertGateRequired(final RiskDecision result, final String expectedGroup, final boolean expectedReversible) {
        final RiskDecision.GateRequired gate = assertInstanceOf(RiskDecision.GateRequired.class, result);
        assertTrue(gate.candidateGroups().contains(expectedGroup),
            "Expected group " + expectedGroup + " in " + gate.candidateGroups());
        assertEquals(expectedReversible, gate.reversible());
        assertNotNull(gate.reason());
        assertFalse(gate.reason().isBlank());
    }

    private void assertGateRequiredWithReason(final RiskDecision result, final String reasonFragment) {
        final RiskDecision.GateRequired gate = assertInstanceOf(RiskDecision.GateRequired.class, result);
        assertTrue(gate.reason().contains(reasonFragment),
            "Expected reason to contain '" + reasonFragment + "' but was: " + gate.reason());
    }
}
