package io.casehub.aml.agents;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import io.casehub.aml.domain.EntityResolutionResult;
import io.casehub.aml.domain.SpecialistOutcome;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.DefaultEntityResolutionService;
import io.casehub.aml.memory.AmlMemoryService;
import io.casehub.qhorus.api.message.Message;

import java.util.Map;

@ApplicationScoped
@DefaultBean
public class EntityResolutionBehaviour implements AgentBehaviour {

    private static final Logger LOG = Logger.getLogger(EntityResolutionBehaviour.class);
    private static final String CAPABILITY = "entity-resolution";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final DefaultEntityResolutionService service = new DefaultEntityResolutionService();

    @Inject AmlMemoryService memoryService;
    @Inject ObjectMapper objectMapper;

    @Override
    public String capability() {
        return CAPABILITY;
    }

    @Override
    public SpecialistOutcome<EntityResolutionResult> handle(final Message command) {
        final SuspiciousTransaction transaction = deserializeTransaction(command);
        final EntityResolutionResult result = service.resolve(transaction);

        memoryService.storeEntityRisk(null, result.entityId(), result);
        if (transaction != null) {
            memoryService.storeNetworkRelationship(null, transaction, result);
        }

        return new SpecialistOutcome.Completed<>(result);
    }

    /**
     * Extracts a {@link SuspiciousTransaction} from a COMMAND message's JSON content.
     *
     * <p>The engine encodes the command payload as:
     * {@code {"type":"COMMAND","capability":"entity-resolution","inputData":{"transaction":{...}},...}}
     *
     * <p>Returns null if the command is null, the content is missing, or any parsing step fails.
     */
    private SuspiciousTransaction deserializeTransaction(final Message command) {
        if (command == null || command.content() == null) {
            return null;
        }
        try {
            final Map<String, Object> payload = objectMapper.readValue(command.content(), MAP_TYPE);
            final Object inputDataRaw = payload.get("inputData");
            if (!(inputDataRaw instanceof Map<?, ?> inputDataMap)) {
                return null;
            }
            final Object txRaw = inputDataMap.get("transaction");
            if (txRaw == null) {
                return null;
            }
            return objectMapper.convertValue(txRaw, SuspiciousTransaction.class);
        } catch (Exception e) {
            LOG.warnf(e, "entity-resolution: failed to deserialize transaction from command content — proceeding without transaction");
            return null;
        }
    }
}
