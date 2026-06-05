package io.casehub.aml.engine;

import io.casehub.aml.domain.SarOutcome;
import java.util.UUID;

/**
 * CDI event fired by {@link AmlLayer6Resource} when a SAR verdict is recorded for an investigation.
 *
 * <p>Decouples the resource from the observer list — each observer (trust attestation, memory
 * storage) self-registers without requiring a direct call in the resource. Adding future observers
 * costs no changes to the resource.
 *
 * @param caseId  the engine case UUID (stable identifier shared across engine event log,
 *                ledger entries, and qhorus messages for this investigation)
 * @param outcome the SAR outcome — verdict, reason, and investigation accuracy score
 */
public record SarOutcomeRecordedEvent(UUID caseId, SarOutcome outcome) {}
