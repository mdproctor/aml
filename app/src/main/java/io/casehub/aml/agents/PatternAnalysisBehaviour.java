package io.casehub.aml.agents;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.aml.domain.PatternAnalysisResult;
import io.casehub.aml.domain.SpecialistOutcome;
import io.casehub.aml.DefaultPatternAnalysisService;
import io.casehub.qhorus.runtime.message.Message;

@ApplicationScoped
@DefaultBean
public class PatternAnalysisBehaviour implements AgentBehaviour {

    private static final String CAPABILITY = "pattern-analysis";

    private final DefaultPatternAnalysisService service = new DefaultPatternAnalysisService();

    @Override
    public String capability() {
        return CAPABILITY;
    }

    @Override
    public SpecialistOutcome<PatternAnalysisResult> handle(Message command) {
        return new SpecialistOutcome.Completed<>(service.analyze(null));
    }
}
