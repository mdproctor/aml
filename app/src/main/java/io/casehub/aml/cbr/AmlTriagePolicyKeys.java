package io.casehub.aml.cbr;

import io.casehub.api.spi.routing.DoublePreference;
import io.casehub.platform.api.preferences.PreferenceKey;

public final class AmlTriagePolicyKeys {
    private static final String NS = "casehubio.aml.triage";

    public static final PreferenceKey<DoublePreference> SAR_THRESHOLD =
            new PreferenceKey<>(NS, "sar-threshold",
                    DoublePreference.of(0.6), DoublePreference::parse);

    public static final PreferenceKey<DoublePreference> FALSE_POSITIVE_THRESHOLD =
            new PreferenceKey<>(NS, "false-positive-threshold",
                    DoublePreference.of(0.25), DoublePreference::parse);

    public static final PreferenceKey<DoublePreference> MAX_CBR_ADJUSTMENT =
            new PreferenceKey<>(NS, "max-cbr-adjustment",
                    DoublePreference.of(0.15), DoublePreference::parse);

    public static final PreferenceKey<DoublePreference> CBR_MIN_CONFIDENCE =
            new PreferenceKey<>(NS, "cbr-min-confidence",
                    DoublePreference.of(0.3), DoublePreference::parse);

    private AmlTriagePolicyKeys() {}
}
