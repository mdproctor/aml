package io.casehub.aml.routing;

import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.platform.api.preferences.PreferenceKey;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;

@ApplicationScoped
public class AmlTrustRoutingPolicyProvider implements TrustRoutingPolicyProvider {

    private static final TrustPolicyPreference SENTINEL =
            new TrustPolicyPreference(0.0, 0, 0.0, 0.0, Map.of());

    private static final Map<String, TrustRoutingPolicy> POLICIES = Map.of(
            "entity-resolution",     new TrustRoutingPolicy(0.70, 10, 0.10, 0.60, Map.of()),
            "pattern-analysis",      new TrustRoutingPolicy(0.65, 10, 0.10, 0.60, Map.of()),
            "osint-screening",       new TrustRoutingPolicy(0.70, 10, 0.10, 0.65, Map.of()),
            "sar-drafting",          new TrustRoutingPolicy(0.75, 10, 0.10, 0.70,
                                         Map.of("investigation-accuracy", 0.65)),
            "senior-analyst-review", new TrustRoutingPolicy(0.80, 10, 0.10, 0.70, Map.of())
    );

    private final PreferenceProvider preferenceProvider;

    @Inject
    public AmlTrustRoutingPolicyProvider(final PreferenceProvider preferenceProvider) {
        this.preferenceProvider = preferenceProvider;
    }

    @Override
    public TrustRoutingPolicy forCapability(final String capabilityName) {
        final PreferenceKey<TrustPolicyPreference> key = new PreferenceKey<>(
                "casehubio.aml.trust-routing",
                capabilityName,
                SENTINEL,
                s -> { throw new UnsupportedOperationException(
                        "TrustPolicyPreference YAML parsing not yet configured — activate casehub-platform-config"); }
        );
        final Preferences prefs =
                preferenceProvider.resolve(SettingsScope.of("casehubio", "aml", "trust-routing", capabilityName));
        final TrustPolicyPreference pref = prefs.get(key);
        if (pref != null && pref != SENTINEL) {
            return new TrustRoutingPolicy(
                    pref.threshold(), pref.minimumObservations(),
                    pref.borderlineMargin(), pref.blendFactor(),
                    pref.qualityFloors());
        }
        return POLICIES.getOrDefault(capabilityName, TrustRoutingPolicy.DEFAULT);
    }
}
