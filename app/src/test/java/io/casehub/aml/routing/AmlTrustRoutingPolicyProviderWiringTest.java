package io.casehub.aml.routing;

import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AmlTrustRoutingPolicyProviderWiringTest {

    @Inject
    TrustRoutingPolicyProvider provider;

    @Test
    void aml_provider_is_selected_over_default_bean() {
        assertInstanceOf(AmlTrustRoutingPolicyProvider.class, provider);
        assertEquals(0.75, provider.forCapability("sar-drafting").threshold(), 0.001);
    }
}
