package io.casehub.aml.engine;

import io.casehub.api.engine.CaseHubRuntime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Service for retrieving prior entity context used when an AML investigation started.
 *
 * <p>Queries the case's {@code priorEntityContext} from the engine runtime, which was
 * populated by {@link io.casehub.aml.memory.AmlMemoryService} before the investigation
 * started. The returned Map contains historical facts about entities, networks, and
 * patterns observed in prior investigations.
 */
@ApplicationScoped
public class AmlInvestigationPriorContextService {

    @Inject
    CaseHubRuntime runtime;

    /**
     * Retrieves the prior entity context for a case.
     *
     * @param caseId The investigation case ID
     * @return The prior context map from {@link io.casehub.aml.memory.AmlPriorContext#toContextMap()}
     */
    @SuppressWarnings("unchecked")
    public CompletionStage<Map<String, Object>> getPriorContext(UUID caseId) {
        return runtime.query(caseId, "priorEntityContext")
            .thenApply(result -> {
                if (result == null) {
                    throw new NotFoundException("Investigation not found or has no prior context: " + caseId);
                }
                return (Map<String, Object>) result;
            })
            .exceptionally(t -> {
                Throwable cause = t instanceof java.util.concurrent.CompletionException ? t.getCause() : t;
                if (cause instanceof NotFoundException) {
                    throw (NotFoundException) cause;
                }
                if (cause instanceof RuntimeException &&
                    cause.getMessage() != null &&
                    cause.getMessage().contains("Case instance not found")) {
                    throw new NotFoundException("Investigation not found: " + caseId);
                }
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(cause);
            });
    }
}
