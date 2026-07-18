package io.casehub.aml.domain;

import io.casehub.neocortex.memory.cbr.FeatureValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CaseProfileTest {

    @Test
    void initial_setsRequiredFields_nullsOptional() {
        CaseProfile profile = CaseProfile.initial(
                FlagReason.STRUCTURING, new BigDecimal("75000.00"), 3);

        assertEquals(FlagReason.STRUCTURING, profile.flagReason());
        assertEquals(new BigDecimal("75000.00"), profile.transactionAmount());
        assertEquals(3, profile.priorIncidentCount());
        assertNull(profile.entityType());
        assertNull(profile.jurisdiction());
        assertNull(profile.network());
    }

    @Test
    void complete_setsAllFields() {
        CaseProfile profile = CaseProfile.complete(
                FlagReason.PEP_MATCH, new BigDecimal("500000"), 5,
                EntityType.PEP, JurisdictionRisk.HIGH, NetworkComplexity.LARGE_NETWORK);

        assertEquals(FlagReason.PEP_MATCH, profile.flagReason());
        assertEquals(EntityType.PEP, profile.entityType());
        assertEquals(JurisdictionRisk.HIGH, profile.jurisdiction());
        assertEquals(NetworkComplexity.LARGE_NETWORK, profile.network());
    }

    @Test
    void toFeatures_initialProfile_emitsThreeDimensions() {
        CaseProfile profile = CaseProfile.initial(
                FlagReason.LAYERING, new BigDecimal("9500"), 0);

        Map<String, FeatureValue> features = profile.toFeatures();

        assertEquals(3, features.size());
        assertEquals(FeatureValue.string("LAYERING"), features.get("flag_reason"));
        assertEquals(FeatureValue.number(9500.0), features.get("transaction_amount"));
        assertEquals(FeatureValue.number(0.0), features.get("prior_incident_count"));
        assertFalse(features.containsKey("entity_type"));
        assertFalse(features.containsKey("jurisdiction_risk"));
        assertFalse(features.containsKey("network_complexity"));
    }

    @Test
    void toFeatures_completeProfile_emitsSixDimensions() {
        CaseProfile profile = CaseProfile.complete(
                FlagReason.SMURFING, new BigDecimal("200000"), 2,
                EntityType.SHELL_COMPANY, JurisdictionRisk.HIGH,
                NetworkComplexity.SMALL_NETWORK);

        Map<String, FeatureValue> features = profile.toFeatures();

        assertEquals(6, features.size());
        assertEquals(FeatureValue.string("SMURFING"), features.get("flag_reason"));
        assertEquals(FeatureValue.number(200000.0), features.get("transaction_amount"));
        assertEquals(FeatureValue.number(2.0), features.get("prior_incident_count"));
        assertEquals(FeatureValue.string("SHELL_COMPANY"), features.get("entity_type"));
        assertEquals(FeatureValue.string("HIGH"), features.get("jurisdiction_risk"));
        assertEquals(FeatureValue.string("SMALL_NETWORK"), features.get("network_complexity"));
    }

    @Test
    void initial_rejectsNullFlagReason() {
        assertThrows(NullPointerException.class, () ->
                CaseProfile.initial(null, new BigDecimal("100"), 0));
    }

    @Test
    void initial_rejectsNullAmount() {
        assertThrows(NullPointerException.class, () ->
                CaseProfile.initial(FlagReason.STRUCTURING, null, 0));
    }
}
