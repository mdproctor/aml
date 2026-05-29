package io.casehub.aml.trust;

import io.casehub.ledger.model.WorkerDecisionEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AmlWorkerDecisionRepository {

    @PersistenceContext(unitName = "qhorus")
    EntityManager em;

    public Optional<WorkerDecisionEntry> findLatestByCaseIdAndCapability(
            final UUID caseId, final String capabilityTag) {
        return em.createQuery(
                "SELECT w FROM WorkerDecisionEntry w" +
                " WHERE w.caseId = :caseId AND w.capabilityTag = :cap" +
                " ORDER BY w.sequenceNumber DESC",
                WorkerDecisionEntry.class)
                .setParameter("caseId", caseId)
                .setParameter("cap", capabilityTag)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    public List<WorkerDecisionEntry> findAllByCaseId(final UUID caseId) {
        return em.createQuery(
                "SELECT w FROM WorkerDecisionEntry w WHERE w.caseId = :caseId ORDER BY w.sequenceNumber ASC",
                WorkerDecisionEntry.class)
                .setParameter("caseId", caseId)
                .getResultList();
    }
}
