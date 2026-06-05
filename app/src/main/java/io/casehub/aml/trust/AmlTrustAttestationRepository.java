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
     * Assigns the next sequence number and persists the attestation in a single
     * {@code REQUIRES_NEW} transaction. Must only be called from within a
     * serialization scope (e.g., a {@code synchronized} block held by the caller)
     * so that the transaction commits before the lock is released, preventing
     * concurrent calls from both reading {@code max=null} and both assigning seq=1.
     *
     * <p>Queries across all {@code LedgerEntry} subtypes — the
     * {@code IDX_LEDGER_ENTRY_SUBJECT_SEQ} constraint is on the base table.
     */
    @Transactional(TxType.REQUIRES_NEW)
    public AmlTrustRoutingAttestation saveWithSequence(AmlTrustRoutingAttestation entry) {
        Integer max = em.createQuery(
                "SELECT MAX(e.sequenceNumber) FROM LedgerEntry e" +
                " WHERE e.subjectId = :sid",
                Integer.class)
                .setParameter("sid", entry.subjectId)
                .getSingleResult();
        entry.sequenceNumber = max == null ? 1 : max + 1;
        em.persist(entry);
        return entry;
    }
}
