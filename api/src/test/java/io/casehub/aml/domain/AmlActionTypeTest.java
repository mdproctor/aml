package io.casehub.aml.domain;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class AmlActionTypeTest {

    // ── fromActionType round-trips ────────────────────────────────────────────

    @Test
    void fromActionType_sarFiling_returnsSarFiling() {
        assertEquals(Optional.of(AmlActionType.SAR_FILING),
            AmlActionType.fromActionType("sar.filing"));
    }

    @Test
    void fromActionType_allConstantsRoundTrip() {
        for (final AmlActionType type : AmlActionType.values()) {
            assertEquals(Optional.of(type),
                AmlActionType.fromActionType(type.actionType()),
                "Round-trip failed for: " + type);
        }
    }

    // ── Safe handling of unknown/null ─────────────────────────────────────────

    @Test
    void fromActionType_null_returnsEmpty() {
        assertEquals(Optional.empty(), AmlActionType.fromActionType(null));
    }

    @Test
    void fromActionType_unknown_returnsEmpty() {
        assertEquals(Optional.empty(), AmlActionType.fromActionType("unknown.type"));
    }

    @Test
    void fromActionType_uppercase_returnsEmpty() {
        assertEquals(Optional.empty(), AmlActionType.fromActionType("SAR_FILING"));
    }

    // ── GatePolicy per constant ───────────────────────────────────────────────

    @Test
    void sarFiling_isAlwaysGated() {
        assertEquals(AmlActionType.GatePolicy.ALWAYS, AmlActionType.SAR_FILING.gatePolicy());
    }

    @Test
    void lawEnforcementReferral_isAlwaysGated() {
        assertEquals(AmlActionType.GatePolicy.ALWAYS, AmlActionType.LAW_ENFORCEMENT_REFERRAL.gatePolicy());
    }

    @Test
    void entityLinkCreation_isRiskScoreThreshold() {
        assertEquals(AmlActionType.GatePolicy.RISK_SCORE_THRESHOLD, AmlActionType.ENTITY_LINK_CREATION.gatePolicy());
    }

    @Test
    void accountRestriction_isRiskScoreThreshold() {
        assertEquals(AmlActionType.GatePolicy.RISK_SCORE_THRESHOLD, AmlActionType.ACCOUNT_RESTRICTION.gatePolicy());
    }

    @Test
    void transactionBlocking_isConfidenceThreshold() {
        assertEquals(AmlActionType.GatePolicy.CONFIDENCE_THRESHOLD, AmlActionType.TRANSACTION_BLOCKING.gatePolicy());
    }

    // ── Reversibility ─────────────────────────────────────────────────────────

    @Test
    void sarFiling_isNotReversible() {
        assertFalse(AmlActionType.SAR_FILING.reversible());
    }

    @Test
    void entityLinkCreation_isReversible() {
        assertTrue(AmlActionType.ENTITY_LINK_CREATION.reversible());
    }

    @Test
    void transactionBlocking_isNotReversible() {
        assertFalse(AmlActionType.TRANSACTION_BLOCKING.reversible());
    }

    // ── Groups ────────────────────────────────────────────────────────────────

    @Test
    void sarFiling_requiresMlro() {
        assertEquals(java.util.List.of(AmlGroups.MLRO), AmlActionType.SAR_FILING.candidateGroups());
    }

    @Test
    void lawEnforcementReferral_requiresSeniorCompliance() {
        assertEquals(java.util.List.of(AmlGroups.AML_SENIOR_COMPLIANCE),
            AmlActionType.LAW_ENFORCEMENT_REFERRAL.candidateGroups());
    }

    @Test
    void entityLinkCreation_requiresAmlCompliance() {
        assertEquals(java.util.List.of(AmlGroups.AML_COMPLIANCE),
            AmlActionType.ENTITY_LINK_CREATION.candidateGroups());
    }

    // ── reason, scope, expiresIn ──────────────────────────────────────────────

    @Test
    void allTypes_haveNonNullReason() {
        for (final AmlActionType type : AmlActionType.values()) {
            assertNotNull(type.reason(), "reason() null for: " + type);
            assertFalse(type.reason().isBlank(), "reason() blank for: " + type);
        }
    }

    @Test
    void allTypes_haveOversightScope() {
        for (final AmlActionType type : AmlActionType.values()) {
            assertEquals("casehubio/aml/oversight", type.scope(), "scope() wrong for: " + type);
        }
    }

    @Test
    void allTypes_expiresInIsNull() {
        for (final AmlActionType type : AmlActionType.values()) {
            assertNull(type.expiresIn(), "expiresIn() non-null for: " + type);
        }
    }

    // ── actionType() format ───────────────────────────────────────────────────

    @Test
    void actionType_usesLowercaseDots() {
        assertEquals("sar.filing", AmlActionType.SAR_FILING.actionType());
        assertEquals("account.restriction", AmlActionType.ACCOUNT_RESTRICTION.actionType());
        assertEquals("transaction.blocking", AmlActionType.TRANSACTION_BLOCKING.actionType());
        assertEquals("entity.link.creation", AmlActionType.ENTITY_LINK_CREATION.actionType());
        assertEquals("law.enforcement.referral", AmlActionType.LAW_ENFORCEMENT_REFERRAL.actionType());
    }
}
