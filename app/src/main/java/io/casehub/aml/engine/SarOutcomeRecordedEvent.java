package io.casehub.aml.engine;

import io.casehub.aml.domain.SarOutcome;
import java.util.UUID;

public record SarOutcomeRecordedEvent(UUID caseId, SarOutcome outcome) {}
