package io.casehub.aml.engine;

import io.casehub.aml.compliance.AmlInvestigationOutcomeService;
import io.casehub.aml.domain.InvestigationOutcome;
import io.casehub.aml.ledger.AmlSarOfficerReviewedLedgerEntry;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AmlInvestigationOutcomeServiceTest {

    @Test
    void returns_null_when_no_officer_review_entry() {
        final AmlInvestigationOutcomeService service = serviceWith(List.of());
        assertNull(service.resolve(UUID.randomUUID()));
    }

    @Test
    void returns_sar_filed_for_approved_human_entry() {
        final AmlSarOfficerReviewedLedgerEntry entry = officerEntry("APPROVED", ActorType.HUMAN);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(entry));
        final InvestigationOutcome outcome = service.resolve(UUID.randomUUID());
        assertNotNull(outcome);
        assertEquals("sar-filed", outcome.type());
    }

    @Test
    void returns_gate_rejected_for_rejected_human_entry() {
        final AmlSarOfficerReviewedLedgerEntry entry = officerEntry("REJECTED", ActorType.HUMAN);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(entry));
        final InvestigationOutcome outcome = service.resolve(UUID.randomUUID());
        assertNotNull(outcome);
        assertEquals("gate-rejected", outcome.type());
    }

    @Test
    void returns_decision_not_recorded_for_unknown_system_entry() {
        final AmlSarOfficerReviewedLedgerEntry entry = officerEntry("UNKNOWN", ActorType.SYSTEM);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(entry));
        final InvestigationOutcome outcome = service.resolve(UUID.randomUUID());
        assertNotNull(outcome);
        assertEquals("decision-not-recorded", outcome.type());
    }

    @Test
    void prefers_human_entry_over_system_entry_in_race() {
        final AmlSarOfficerReviewedLedgerEntry humanEntry = officerEntry("APPROVED", ActorType.HUMAN);
        final AmlSarOfficerReviewedLedgerEntry systemEntry = officerEntry("UNKNOWN", ActorType.SYSTEM);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(humanEntry, systemEntry));
        final InvestigationOutcome outcome = service.resolve(UUID.randomUUID());
        assertNotNull(outcome);
        assertEquals("sar-filed", outcome.type());
    }

    @Test
    void prefers_human_entry_regardless_of_list_order() {
        final AmlSarOfficerReviewedLedgerEntry humanEntry = officerEntry("REJECTED", ActorType.HUMAN);
        final AmlSarOfficerReviewedLedgerEntry systemEntry = officerEntry("UNKNOWN", ActorType.SYSTEM);
        // SYSTEM entry first in list
        final AmlInvestigationOutcomeService service = serviceWith(List.of(systemEntry, humanEntry));
        final InvestigationOutcome outcome = service.resolve(UUID.randomUUID());
        assertNotNull(outcome);
        assertEquals("gate-rejected", outcome.type());
    }

    private static AmlSarOfficerReviewedLedgerEntry officerEntry(final String decision,
            final ActorType actorType) {
        final AmlSarOfficerReviewedLedgerEntry entry = new AmlSarOfficerReviewedLedgerEntry();
        entry.reviewDecision = decision;
        entry.actorType = actorType;
        return entry;
    }

    private static AmlInvestigationOutcomeService serviceWith(
            final List<LedgerEntry> entries) {
        final LedgerEntryRepository repo = new LedgerEntryRepository() {
            @Override
            public LedgerEntry save(LedgerEntry entry, String tenancyId) {
                return null;
            }

            @Override
            public List<LedgerEntry> findBySubjectId(UUID subjectId, String tenancyId) {
                return entries;
            }

            @Override
            public List<LedgerEntry> findBySubjectIdAndTimeRange(UUID subjectId, Instant from, Instant to, String tenancyId) {
                return List.of();
            }

            @Override
            public Optional<LedgerEntry> findLatestBySubjectId(UUID subjectId, String tenancyId) {
                return Optional.empty();
            }

            @Override
            public Optional<LedgerEntry> findEntryById(UUID id, String tenancyId) {
                return Optional.empty();
            }

            @Override
            public List<LedgerAttestation> findAttestationsByEntryId(UUID ledgerEntryId, String tenancyId) {
                return List.of();
            }

            @Override
            public LedgerAttestation saveAttestation(LedgerAttestation attestation, String tenancyId) {
                return null;
            }

            @Override
            public List<LedgerEntry> findByActorId(String actorId, Instant from, Instant to, String tenancyId) {
                return List.of();
            }

            @Override
            public List<LedgerEntry> findByActorRole(String actorRole, Instant from, Instant to, String tenancyId) {
                return List.of();
            }

            @Override
            public List<LedgerEntry> findCausedBy(UUID entryId, String tenancyId) {
                return List.of();
            }

            @Override
            public List<LedgerAttestation> findAttestationsByEntryIdAndCapabilityTag(UUID entryId, String capabilityTag, String tenancyId) {
                return List.of();
            }

            @Override
            public List<LedgerAttestation> findAttestationsByEntryIdGlobal(UUID entryId, String tenancyId) {
                return List.of();
            }

            @Override
            public List<LedgerAttestation> findAttestationsByAttestorIdAndCapabilityTag(String attestorId, String capabilityTag, String tenancyId) {
                return List.of();
            }
        };
        return new AmlInvestigationOutcomeService(repo);
    }
}
