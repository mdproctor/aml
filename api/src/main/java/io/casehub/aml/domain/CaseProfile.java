package io.casehub.aml.domain;

import io.casehub.neocortex.memory.cbr.FeatureValue;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record CaseProfile(
        FlagReason flagReason,
        BigDecimal transactionAmount,
        int priorIncidentCount,
        EntityType entityType,
        JurisdictionRisk jurisdiction,
        NetworkComplexity network
) {
    public CaseProfile {
        Objects.requireNonNull(flagReason, "flagReason");
        Objects.requireNonNull(transactionAmount, "transactionAmount");
    }

    public static CaseProfile initial(FlagReason flagReason, BigDecimal transactionAmount,
                                      int priorIncidentCount) {
        return new CaseProfile(flagReason, transactionAmount, priorIncidentCount,
                null, null, null);
    }

    public static CaseProfile complete(FlagReason flagReason, BigDecimal transactionAmount,
                                       int priorIncidentCount, EntityType entityType,
                                       JurisdictionRisk jurisdiction, NetworkComplexity network) {
        return new CaseProfile(flagReason, transactionAmount, priorIncidentCount,
                entityType, jurisdiction, network);
    }

    public Map<String, FeatureValue> toFeatures() {
        var map = new LinkedHashMap<String, FeatureValue>();
        map.put("flag_reason", FeatureValue.string(flagReason.name()));
        map.put("transaction_amount", FeatureValue.number(transactionAmount.doubleValue()));
        map.put("prior_incident_count", FeatureValue.number(priorIncidentCount));
        if (entityType != null) map.put("entity_type", FeatureValue.string(entityType.name()));
        if (jurisdiction != null) map.put("jurisdiction_risk", FeatureValue.string(jurisdiction.name()));
        if (network != null) map.put("network_complexity", FeatureValue.string(network.name()));
        return Map.copyOf(map);
    }
}
