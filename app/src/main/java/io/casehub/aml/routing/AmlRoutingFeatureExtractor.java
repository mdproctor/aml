package io.casehub.aml.routing;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.blocks.routing.agent.RoutingFeatureExtractor;
import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class AmlRoutingFeatureExtractor implements RoutingFeatureExtractor {

    @Override
    public Map<String, Object> extractFeatures(AgentRoutingContext context) {
        JsonNode root = context.caseContext();
        if (root == null || root.isNull()) {
            return Map.of();
        }

        Map<String, Object> features = new LinkedHashMap<>();
        JsonNode prior = root.path("priorEntityContext");
        if (!prior.isMissingNode()) {
            putIfPresent(features, "knownHighRisk", prior, JsonNode::asBoolean);
            putIntIfPresent(features, "entityRiskCount", prior);
            putIntIfPresent(features, "networkCount", prior);
            putIntIfPresent(features, "patternCount", prior);
        }

        JsonNode er = root.path("entityResolution");
        if (!er.isMissingNode()) {
            putStringIfPresent(features, "entityType", er);
            putDoubleIfPresent(features, "riskScore", er);
        }

        return Map.copyOf(features);
    }

    @Override
    public @Nullable String extractProblem(AgentRoutingContext context) {
        JsonNode root = context.caseContext();
        if (root == null || root.isNull()) {
            return null;
        }

        JsonNode prior = root.path("priorEntityContext");
        JsonNode er = root.path("entityResolution");

        if (!er.isMissingNode() && er.has("entityType")) {
            return String.format("entityType=%s, riskScore=%s, %d prior entity risks",
                    er.path("entityType").asText(),
                    er.has("riskScore") ? String.valueOf(er.path("riskScore").asDouble()) : "unknown",
                    prior.path("entityRiskCount").asInt(0));
        }

        if (!prior.isMissingNode() && prior.has("entityRiskCount")) {
            var sb = new StringBuilder();
            sb.append(prior.path("entityRiskCount").asInt(0)).append(" prior entity risks");
            if (prior.path("networkCount").asInt(0) > 0) {
                sb.append(", ").append(prior.path("networkCount").asInt(0)).append(" network patterns");
            }
            if (prior.path("knownHighRisk").asBoolean(false)) {
                sb.append(", known high risk");
            }
            return sb.toString();
        }

        return null;
    }

    private static void putIfPresent(Map<String, Object> map, String key,
                                     JsonNode parent, java.util.function.Function<JsonNode, Object> converter) {
        JsonNode child = parent.path(key);
        if (!child.isMissingNode()) {
            map.put(key, converter.apply(child));
        }
    }

    private static void putIntIfPresent(Map<String, Object> map, String key, JsonNode parent) {
        JsonNode child = parent.path(key);
        if (!child.isMissingNode() && child.isInt()) {
            map.put(key, child.asInt());
        }
    }

    private static void putDoubleIfPresent(Map<String, Object> map, String key, JsonNode parent) {
        JsonNode child = parent.path(key);
        if (!child.isMissingNode() && child.isNumber()) {
            map.put(key, child.asDouble());
        }
    }

    private static void putStringIfPresent(Map<String, Object> map, String key, JsonNode parent) {
        JsonNode child = parent.path(key);
        if (!child.isMissingNode() && child.isTextual()) {
            map.put(key, child.asText());
        }
    }
}
