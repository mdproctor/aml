package io.casehub.aml.routing;

import io.casehub.platform.api.preferences.PreferenceKey;
import java.util.Map;

/**
 * PreferenceKey constants for AML trust routing YAML configuration.
 * Resolved at scope: casehubio/aml/trust-routing/&lt;capabilityName&gt;
 */
public final class TrustRoutingPolicyKeys {

    public static final PreferenceKey<DoublePreference> THRESHOLD =
        new PreferenceKey<>("casehubio.aml.trust-routing", "threshold",
            DoublePreference.of(0.0), DoublePreference::parse);

    public static final PreferenceKey<IntPreference> MINIMUM_OBSERVATIONS =
        new PreferenceKey<>("casehubio.aml.trust-routing", "minimum-observations",
            IntPreference.of(0), IntPreference::parse);

    public static final PreferenceKey<DoublePreference> BORDERLINE_MARGIN =
        new PreferenceKey<>("casehubio.aml.trust-routing", "borderline-margin",
            DoublePreference.of(0.0), DoublePreference::parse);

    public static final PreferenceKey<DoublePreference> BLEND_FACTOR =
        new PreferenceKey<>("casehubio.aml.trust-routing", "blend-factor",
            DoublePreference.of(0.0), DoublePreference::parse);

    /** Minimum investigation-accuracy dimension score. 0.0 = no floor. */
    public static final PreferenceKey<DoublePreference> FLOOR_INVESTIGATION_ACCURACY =
        new PreferenceKey<>("casehubio.aml.trust-routing", "floor.investigation-accuracy",
            DoublePreference.of(0.0), DoublePreference::parse);

    private static final Map<String, PreferenceKey<DoublePreference>> FLOOR_KEYS = Map.of(
        "investigation-accuracy", FLOOR_INVESTIGATION_ACCURACY
    );

    public static Map<String, PreferenceKey<DoublePreference>> allFloorKeys() {
        return FLOOR_KEYS;
    }

    private TrustRoutingPolicyKeys() {}
}
