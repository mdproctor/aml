package io.casehub.aml.engine;

import io.casehub.api.model.cbr.CbrConfig.CbrRetrievalTiming;
import io.casehub.api.model.evaluator.JQExpressionEvaluator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AmlInvestigationCaseHubTest {

    @Inject
    AmlInvestigationCaseHub caseHub;

    @Test
    void definitionLoads() {
        final var def = caseHub.getDefinition();
        assertNotNull(def);
        assertEquals("casehub-aml", def.getNamespace());
        assertEquals("aml-investigation", def.getName());
    }

    @Test
    void hasSixCapabilities() {
        final var names = caseHub.getDefinition().getCapabilities()
                                 .stream().map(c -> c.name()).toList();
        assertEquals(6, names.size());
        assertTrue(names.containsAll(List.of(
                "entity-resolution", "pattern-analysis", "osint-screening",
                "senior-analyst-review", "sar-drafting", "compliance-review-opening")));
    }

    @Test
    void hasSevenBindings() {
        // senior-analyst-required split into two bindings (prior-context + resolution)
        // to prevent double-dispatch race in async Quartz execution
        final var names = caseHub.getDefinition().getBindings()
                                 .stream().map(b -> b.getName()).toList();
        assertEquals(7, names.size());
        assertTrue(names.containsAll(List.of(
                "entity-resolution", "pattern-analysis", "osint-screening",
                "senior-analyst-required-prior-context", "senior-analyst-required-resolution",
                "sar-drafting", "compliance-review-opening")));
    }

    @Test
    void hasInvestigationCompleteGoal() {
        final var goals = caseHub.getDefinition().getGoals();
        assertEquals(1, goals.size());
        assertEquals("investigation-complete", goals.get(0).getName());
        assertTrue(goals.get(0).getCondition() instanceof JQExpressionEvaluator jq
                   && jq.expression().contains("complianceTaskId"),
                   "Goal condition should check complianceTaskId");
    }

    @Test
    void hasEightWorkers() {
        final var workers = caseHub.getDefinition().getWorkers();
        assertEquals(8, workers.size(), "Exactly 8 workers expected — size catches double-augmentation");
        final var names = Set.copyOf(workers.stream().map(w -> w.name()).toList());
        assertEquals(Set.of(
                "entity-resolution-agent", "pattern-analysis-agent",
                "osint-screening-agent", "osint-screening-agent-senior",
                "senior-analyst-agent",
                "sar-drafting-agent-junior", "sar-drafting-agent-senior",
                "compliance-review-opening-agent"), names);
    }

    @Test
    void hasCbrConfig() {
        final var config = caseHub.getDefinition().getCbrConfig();
        assertNotNull(config, "CbrConfig must be set for CBR retrieval");
        assertEquals("aml-investigation", config.caseType());
        assertEquals("aml.cbr", config.domain());
        assertEquals(10, config.topK());
        assertEquals(0.5, config.minSimilarity(), 0.001);
        assertEquals(0.0, config.vectorWeight(), 0.001);
        assertEquals(CbrRetrievalTiming.CASE_LIFETIME, config.timing());
    }

}
