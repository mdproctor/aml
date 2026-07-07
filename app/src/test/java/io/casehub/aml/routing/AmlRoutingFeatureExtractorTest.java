package io.casehub.aml.routing;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.api.spi.routing.AgentRoutingContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AmlRoutingFeatureExtractorTest {

    private final AmlRoutingFeatureExtractor extractor = new AmlRoutingFeatureExtractor();

    private AgentRoutingContext contextWith(ObjectNode caseContext) {
        return new AgentRoutingContext(UUID.randomUUID(), "pattern-analysis", caseContext, "tenant-1", List.of());
    }

    @Test
    void extractFeatures_fullContext_returnsAllFeatures() {
        var node = JsonNodeFactory.instance.objectNode();
        var prior = node.putObject("priorEntityContext");
        prior.put("knownHighRisk", true);
        prior.put("entityRiskCount", 5);
        prior.put("networkCount", 3);
        prior.put("patternCount", 2);
        var er = node.putObject("entityResolution");
        er.put("entityType", "PEP");
        er.put("riskScore", 0.87);

        var features = extractor.extractFeatures(contextWith(node));

        assertThat(features)
                .containsEntry("knownHighRisk", true)
                .containsEntry("entityRiskCount", 5)
                .containsEntry("networkCount", 3)
                .containsEntry("patternCount", 2)
                .containsEntry("entityType", "PEP")
                .containsEntry("riskScore", 0.87)
                .hasSize(6);
    }

    @Test
    void extractFeatures_sparseContext_returnsPriorFeaturesOnly() {
        var node = JsonNodeFactory.instance.objectNode();
        var prior = node.putObject("priorEntityContext");
        prior.put("knownHighRisk", false);
        prior.put("entityRiskCount", 2);
        prior.put("networkCount", 0);
        prior.put("patternCount", 1);
        // no entityResolution node

        var features = extractor.extractFeatures(contextWith(node));

        assertThat(features)
                .containsEntry("knownHighRisk", false)
                .containsEntry("entityRiskCount", 2)
                .containsEntry("networkCount", 0)
                .containsEntry("patternCount", 1)
                .doesNotContainKey("entityType")
                .doesNotContainKey("riskScore")
                .hasSize(4);
    }

    @Test
    void extractFeatures_nullNode_returnsEmpty() {
        var ctx = new AgentRoutingContext(UUID.randomUUID(), "analysis", NullNode.instance, "t", List.of());
        assertThat(extractor.extractFeatures(ctx)).isEmpty();
    }

    @Test
    void extractProblem_nullNode_returnsNull() {
        var ctx = new AgentRoutingContext(UUID.randomUUID(), "analysis", NullNode.instance, "t", List.of());
        assertThat(extractor.extractProblem(ctx)).isNull();
    }

    @Test
    void extractProblem_withEntityResolution_includesEntityInfo() {
        var node = JsonNodeFactory.instance.objectNode();
        var prior = node.putObject("priorEntityContext");
        prior.put("entityRiskCount", 3);
        var er = node.putObject("entityResolution");
        er.put("entityType", "PEP");
        er.put("riskScore", 0.87);

        var problem = extractor.extractProblem(contextWith(node));

        assertThat(problem).isEqualTo("entityType=PEP, riskScore=0.87, 3 prior entity risks");
    }

    @Test
    void extractProblem_withoutEntityResolution_usesCountsAndFlags() {
        var node = JsonNodeFactory.instance.objectNode();
        var prior = node.putObject("priorEntityContext");
        prior.put("knownHighRisk", true);
        prior.put("entityRiskCount", 5);
        prior.put("networkCount", 2);
        prior.put("patternCount", 0);

        var problem = extractor.extractProblem(contextWith(node));

        assertThat(problem).isEqualTo("5 prior entity risks, 2 network patterns, known high risk");
    }
}
