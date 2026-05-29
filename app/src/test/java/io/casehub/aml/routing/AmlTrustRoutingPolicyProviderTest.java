package io.casehub.aml.routing;

import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AmlTrustRoutingPolicyProviderTest {

    @Inject
    AmlTrustRoutingPolicyProvider provider;

    @Test
    void sar_drafting_has_higher_threshold_than_default() {
        final TrustRoutingPolicy policy = provider.forCapability("sar-drafting");
        assertEquals(0.75, policy.threshold(), 0.001);
        assertEquals(0.70, policy.blendFactor(), 0.001);
        assertEquals(10, policy.minimumObservations());
        assertTrue(policy.qualityFloors().containsKey("investigation-accuracy"));
        assertEquals(0.65, policy.qualityFloors().get("investigation-accuracy"), 0.001);
    }

    @Test
    void osint_screening_threshold_is_0_70() {
        final TrustRoutingPolicy policy = provider.forCapability("osint-screening");
        assertEquals(0.70, policy.threshold(), 0.001);
        assertEquals(0.65, policy.blendFactor(), 0.001);
        assertTrue(policy.qualityFloors().isEmpty());
    }

    @Test
    void pattern_analysis_threshold_is_0_65() {
        final TrustRoutingPolicy policy = provider.forCapability("pattern-analysis");
        assertEquals(0.65, policy.threshold(), 0.001);
    }

    @Test
    void senior_analyst_review_has_highest_threshold() {
        final TrustRoutingPolicy policy = provider.forCapability("senior-analyst-review");
        assertEquals(0.80, policy.threshold(), 0.001);
    }

    @Test
    void unknown_capability_returns_default_policy() {
        final TrustRoutingPolicy policy = provider.forCapability("unknown-capability");
        assertEquals(TrustRoutingPolicy.DEFAULT.threshold(), policy.threshold(), 0.001);
    }

    @Test
    void mock_preferences_return_null_so_hardcoded_fallback_always_applies() {
        final TrustRoutingPolicy policy = provider.forCapability("sar-drafting");
        assertNotNull(policy);
        assertEquals(0.75, policy.threshold(), 0.001);
    }
}
