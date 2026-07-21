package io.casehub.aml.cbr;

import io.casehub.aml.domain.TriageDecision;
import io.casehub.engine.flow.FlowWorkerFunction;
import io.casehub.worker.api.Worker;

import java.util.Map;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;

public final class InvestigationTriageWorker {

    private InvestigationTriageWorker() {}

    public static Worker create() {
        return Worker.builder()
                     .name("investigation-triage-agent")
                     .capabilityName("investigation-triage")
                     .function(new FlowWorkerFunction(
                             workflow("investigation-triage")
                                     .tasks(
                                             function(s -> Map.of(
                                                     "decision", TriageDecision.SAR_WARRANTED.name(),
                                                     "reason", "stub — real triage logic pending #112"
                                             ), Map.class))
                                     .build()))
                     .build();
    }
}
