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
     * Returns the next sequence number for the given ledger subject.
     *
     * <p>Queries across all {@code LedgerEntry} subtypes — the
     * {@code IDX_LEDGER_ENTRY_SUBJECT_SEQ} constraint is on the base table, so any
     * subclass entry sharing the same subject would cause a conflict if this query
     * were scoped to a single subtype.
     *
     * <p>In practice the caller always passes the attestation-specific subject (derived
     * via {@link AmlTrustRoutingObserver#attestationSubjectFor}), so only attestation
     * entries are present for that subject and the result is always correct.
     */
    @Transactional(TxType.REQUIRED)
    public int nextSequenceNumber(UUID subjectId) {
        Integer max = em.createQuery(
                "SELECT MAX(e.sequenceNumber) FROM LedgerEntry e" +
                " WHERE e.subjectId = :sid",
                Integer.class)
                .setParameter("sid", subjectId)
                .getSingleResult();
        return max == null ? 1 : max + 1;
    }
}
