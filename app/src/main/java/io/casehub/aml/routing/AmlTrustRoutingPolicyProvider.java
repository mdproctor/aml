package io.casehub.aml.routing;

import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.platform.api.preferences.PreferenceKey;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class AmlTrustRoutingPolicyProvider implements TrustRoutingPolicyProvider {

    private final PreferenceProvider preferenceProvider;

    @Inject
    public AmlTrustRoutingPolicyProvider(final PreferenceProvider preferenceProvider) {
        this.preferenceProvider = preferenceProvider;
    }

    @Override
    public TrustRoutingPolicy forCapability(final String capabilityName) {
        final Preferences prefs = preferenceProvider.resolve(
            SettingsScope.of("casehubio", "aml", "trust-routing", capabilityName));

        final DoublePreference threshold = prefs.get(TrustRoutingPolicyKeys.THRESHOLD);
        if (threshold == null) {
            return TrustRoutingPolicy.DEFAULT;
        }

        final IntPreference minObs = prefs.get(TrustRoutingPolicyKeys.MINIMUM_OBSERVATIONS);
        final DoublePreference borderlineMargin = prefs.get(TrustRoutingPolicyKeys.BORDERLINE_MARGIN);
        final DoublePreference blendFactor = prefs.get(TrustRoutingPolicyKeys.BLEND_FACTOR);

        final Map<String, Double> qualityFloors = new HashMap<>();
        TrustRoutingPolicyKeys.allFloorKeys().forEach((dimension, key) ->
            addFloor(qualityFloors, prefs, key, dimension));

        return new TrustRoutingPolicy(
            threshold.value(),
            minObs != null ? minObs.value() : TrustRoutingPolicy.DEFAULT.minimumObservations(),
            borderlineMargin != null ? borderlineMargin.value() : TrustRoutingPolicy.DEFAULT.borderlineMargin(),
            blendFactor != null ? blendFactor.value() : TrustRoutingPolicy.DEFAULT.blendFactor(),
            Map.copyOf(qualityFloors),
            TrustRoutingPolicy.DEFAULT.bootstrapEscalationRequired());
    }

    public Set<String> capabilities() {
        return Set.of("entity-resolution", "pattern-analysis", "osint-screening",
                      "sar-drafting", "senior-analyst-review");
    }

    private static void addFloor(final Map<String, Double> floors, final Preferences prefs,
            final PreferenceKey<DoublePreference> key, final String dimension) {
        final DoublePreference value = prefs.get(key);
        if (value != null && value.value() > 0.0) {
            floors.put(dimension, value.value());
        }
    }
}
