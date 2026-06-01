package io.casehub.aml.routing;

import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.platform.api.preferences.MapPreferences;
import io.casehub.platform.api.preferences.PreferenceProvider;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AmlTrustRoutingPolicyProviderTest {

    private static final PreferenceProvider EMPTY = scope -> new MapPreferences(Map.of());

    private static final PreferenceProvider SAR_DRAFTING = scope ->
        new MapPreferences(Map.of(
            "casehubio.aml.trust-routing.threshold", "0.75",
            "casehubio.aml.trust-routing.minimum-observations", "10",
            "casehubio.aml.trust-routing.borderline-margin", "0.10",
            "casehubio.aml.trust-routing.blend-factor", "0.70",
            "casehubio.aml.trust-routing.floor.investigation-accuracy", "0.65"
        ));

    private static final PreferenceProvider OSINT_SCREENING = scope ->
        new MapPreferences(Map.of(
            "casehubio.aml.trust-routing.threshold", "0.70",
            "casehubio.aml.trust-routing.minimum-observations", "10",
            "casehubio.aml.trust-routing.borderline-margin", "0.10",
            "casehubio.aml.trust-routing.blend-factor", "0.65"
        ));

    @Test
    void unknownCapabilityReturnsDefault() {
        var provider = new AmlTrustRoutingPolicyProvider(EMPTY);
        assertEquals(TrustRoutingPolicy.DEFAULT, provider.forCapability("unknown-capability"));
    }

    @Test
    void sarDraftingThreshold() {
        var provider = new AmlTrustRoutingPolicyProvider(SAR_DRAFTING);
        assertEquals(0.75, provider.forCapability("sar-drafting").threshold(), 0.001);
    }

    @Test
    void sarDraftingMinimumObservations() {
        var provider = new AmlTrustRoutingPolicyProvider(SAR_DRAFTING);
        assertEquals(10, provider.forCapability("sar-drafting").minimumObservations());
    }

    @Test
    void sarDraftingBlendFactor() {
        var provider = new AmlTrustRoutingPolicyProvider(SAR_DRAFTING);
        assertEquals(0.70, provider.forCapability("sar-drafting").blendFactor(), 0.001);
    }

    @Test
    void sarDraftingInvestigationAccuracyFloor() {
        var provider = new AmlTrustRoutingPolicyProvider(SAR_DRAFTING);
        Map<String, Double> floors = provider.forCapability("sar-drafting").qualityFloors();
        assertTrue(floors.containsKey("investigation-accuracy"));
        assertEquals(0.65, floors.get("investigation-accuracy"), 0.001);
    }

    @Test
    void osintScreeningThreshold() {
        var provider = new AmlTrustRoutingPolicyProvider(OSINT_SCREENING);
        assertEquals(0.70, provider.forCapability("osint-screening").threshold(), 0.001);
    }

    @Test
    void osintScreeningBlendFactor() {
        var provider = new AmlTrustRoutingPolicyProvider(OSINT_SCREENING);
        assertEquals(0.65, provider.forCapability("osint-screening").blendFactor(), 0.001);
    }

    @Test
    void osintScreeningHasNoQualityFloors() {
        var provider = new AmlTrustRoutingPolicyProvider(OSINT_SCREENING);
        assertTrue(provider.forCapability("osint-screening").qualityFloors().isEmpty());
    }

    @Test
    void zeroFloorValueNotAddedToMap() {
        PreferenceProvider withZeroFloor = scope -> new MapPreferences(Map.of(
            "casehubio.aml.trust-routing.threshold", "0.70",
            "casehubio.aml.trust-routing.floor.investigation-accuracy", "0.0"
        ));
        var provider = new AmlTrustRoutingPolicyProvider(withZeroFloor);
        assertFalse(provider.forCapability("any").qualityFloors().containsKey("investigation-accuracy"));
    }
}
