package io.casehub.aml.trust;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AmlTrustAttestationRepository {

    @PersistenceContext(unitName = "qhorus")
    EntityManager em;

    @Transactional(TxType.REQUIRED)
    public AmlTrustRoutingAttestation save(AmlTrustRoutingAttestation entry) {
        em.persist(entry);
        return entry;
    }

    @Transactional(TxType.REQUIRED)
    public List<AmlTrustRoutingAttestation> findByInvestigationCaseId(UUID caseId) {
        return em.createQuery(
                "SELECT a FROM AmlTrustRoutingAttestation a" +
                " WHERE a.investigationCaseId = :caseId ORDER BY a.occurredAt ASC",
                AmlTrustRoutingAttestation.class)
                .setParameter("caseId", caseId)
                .getResultList();
    }

    /**
     * Returns the next sequence number for attestation entries under the given subjectId,
     * scoped to the qhorus persistence unit only.
     * Deliberately does NOT query the shared LedgerEntry sequence to avoid Merkle frontier
     * contention with the engine's own ledger writers on the same subjectId.
     */
    @Transactional(TxType.REQUIRED)
    public int nextSequenceNumber(UUID subjectId) {
        Integer max = em.createQuery(
                "SELECT MAX(a.sequenceNumber) FROM AmlTrustRoutingAttestation a" +
                " WHERE a.subjectId = :sid",
                Integer.class)
                .setParameter("sid", subjectId)
                .getSingleResult();
        return max == null ? 1 : max + 1;
    }
}
