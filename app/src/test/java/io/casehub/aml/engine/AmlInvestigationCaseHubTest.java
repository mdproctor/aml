package io.casehub.aml.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import io.casehub.api.model.evaluator.JQExpressionEvaluator;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

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
    void hasFiveCapabilities() {
        final var names = caseHub.getDefinition().getCapabilities()
                .stream().map(c -> c.name()).toList();
        assertEquals(5, names.size());
        assertTrue(names.containsAll(List.of(
                "entity-resolution", "pattern-analysis", "osint-screening",
                "senior-analyst-review", "sar-drafting")));
    }

    @Test
    void hasSixBindings() {
        // senior-analyst-required split into two bindings (prior-context + resolution)
        // to prevent double-dispatch race in async Quartz execution
        final var names = caseHub.getDefinition().getBindings()
                .stream().map(b -> b.getName()).toList();
        assertEquals(6, names.size());
        assertTrue(names.containsAll(List.of(
                "entity-resolution", "pattern-analysis", "osint-screening",
                "senior-analyst-required-prior-context", "senior-analyst-required-resolution",
                "sar-drafting")));
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
    void hasSevenWorkers() {
        final var workers = caseHub.getDefinition().getWorkers();
        assertEquals(7, workers.size(), "Exactly 7 workers expected — size catches double-augmentation");
        final var names = Set.copyOf(workers.stream().map(w -> w.name()).toList());
        assertEquals(Set.of(
                "entity-resolution-agent", "pattern-analysis-agent",
                "osint-screening-agent", "osint-screening-agent-senior",
                "senior-analyst-agent",
                "sar-drafting-agent-junior", "sar-drafting-agent-senior"), names);
    }
}
