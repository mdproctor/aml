package io.casehub.aml.memory;

import io.casehub.aml.routing.IntPreference;
import io.casehub.platform.api.preferences.PreferenceKey;

public final class AmlMemoryPolicyKeys {
    /** Lookback window for entity-risk memory queries. Default: 365 days. */
    public static final PreferenceKey<IntPreference> ENTITY_RISK_LOOKBACK_DAYS =
        new PreferenceKey<>("casehubio.aml.memory", "entity-risk-lookback-days",
            IntPreference.of(365), IntPreference::parse);

    private AmlMemoryPolicyKeys() {}
}
