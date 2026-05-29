package io.casehub.aml.routing;

import io.casehub.platform.api.preferences.SingleValuePreference;
import java.util.Map;

public record TrustPolicyPreference(
        double threshold,
        int minimumObservations,
        double borderlineMargin,
        double blendFactor,
        Map<String, Double> qualityFloors) implements SingleValuePreference {}
