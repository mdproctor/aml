package io.casehub.aml.engine;

import io.casehub.api.model.Worker;
import io.casehub.api.model.WorkerFunction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

class AmlInvestigationCaseDescriptorTest {

    // null deps are safe: workers() builds lambda closures but does not invoke them
    private final AmlInvestigationCaseDescriptor descriptor =
            new AmlInvestigationCaseDescriptor(null, null);

    @Test
    void workers_returnsSevenDistinctWorkers() {
        final List<Worker> workers = descriptor.workers();
        assertEquals(7, workers.size(), "Descriptor must declare exactly 7 workers");
        final Set<String> names = workers.stream().map(Worker::getName).collect(Collectors.toSet());
        assertEquals(7, names.size(), "All worker names must be distinct");
        assertEquals(Set.of(
                "entity-resolution-agent",
                "pattern-analysis-agent",
                "osint-screening-agent",
                "osint-screening-agent-senior",
                "senior-analyst-agent",
                "sar-drafting-agent-junior",
                "sar-drafting-agent-senior"), names);
    }

    @Test
    void each_worker_declares_exactly_one_capability() {
        for (final Worker w : descriptor.workers()) {
            assertEquals(1, w.getCapabilities().size(),
                    "Worker " + w.getName() + " must declare exactly one capability");
        }
    }

    @Test
    void capability_names_match_expected_tags() {
        final var capByWorker = descriptor.workers().stream()
                .collect(Collectors.toMap(
                        Worker::getName,
                        w -> w.getCapabilities().get(0).getName()));

        assertEquals("entity-resolution",   capByWorker.get("entity-resolution-agent"));
        assertEquals("pattern-analysis",    capByWorker.get("pattern-analysis-agent"));
        assertEquals("osint-screening",     capByWorker.get("osint-screening-agent"));
        assertEquals("osint-screening",     capByWorker.get("osint-screening-agent-senior"));
        assertEquals("senior-analyst-review", capByWorker.get("senior-analyst-agent"));
        assertEquals("sar-drafting",        capByWorker.get("sar-drafting-agent-junior"));
        assertEquals("sar-drafting",        capByWorker.get("sar-drafting-agent-senior"));
    }

    @Test
    void each_worker_has_non_null_function() {
        for (final Worker w : descriptor.workers()) {
            assertNotNull(w.getFunction(),
                    "Worker " + w.getName() + " must have a non-null function");
        }
    }

    @Test
    void worker_execution_model_classification_is_exhaustive() {
        // Pure computation workers must use WorkerFunction.Flow (FuncWorkflowBuilder).
        // SAR drafting workers remain Sync pending engine WorkerExecutionContext support (#66).
        // Any new worker must be explicitly classified here — this prevents silent omissions.
        // Protocol PP-20260531-worker-func-exec.
        final Set<String> pureComputationWorkers = Set.of(
                "entity-resolution-agent",
                "pattern-analysis-agent",
                "osint-screening-agent",
                "osint-screening-agent-senior",
                "senior-analyst-agent");
        final Set<String> sarWorkers = Set.of(
                "sar-drafting-agent-junior",
                "sar-drafting-agent-senior");

        for (final Worker w : descriptor.workers()) {
            if (pureComputationWorkers.contains(w.getName())) {
                assertInstanceOf(WorkerFunction.Flow.class, w.getFunction(),
                        "Worker " + w.getName() + " must use WorkerFunction.Flow (FuncWorkflowBuilder).");
            } else if (sarWorkers.contains(w.getName())) {
                assertInstanceOf(WorkerFunction.Sync.class, w.getFunction(),
                        "SAR worker " + w.getName() + " must remain Sync until engine"
                                + " provides WorkerExecutionContext in the flow path (#66).");
            } else {
                fail("Worker " + w.getName() + " is unclassified — add it to pureComputationWorkers"
                        + " or sarWorkers in this test to make the execution model explicit.");
            }
        }
    }
}
