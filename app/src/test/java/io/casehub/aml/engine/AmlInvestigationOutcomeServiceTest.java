package io.casehub.aml.engine;

import io.casehub.aml.domain.InvestigationOutcome;
import io.casehub.aml.domain.InvestigationResolution;
import io.casehub.aml.domain.InvestigationStatus;
import io.casehub.aml.ledger.AmlSarOfficerReviewedLedgerEntry;
import io.casehub.api.model.CaseStatus;
import io.casehub.engine.common.internal.history.EventLog;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.CaseInstanceRepository;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import io.smallrye.mutiny.Uni;
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
        assertNull(service.resolveOutcome(UUID.randomUUID()));
    }

    @Test
    void returns_sar_filed_for_approved_human_entry() {
        final AmlSarOfficerReviewedLedgerEntry entry = officerEntry("APPROVED", ActorType.HUMAN);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(entry));
        final InvestigationOutcome outcome = service.resolveOutcome(UUID.randomUUID());
        assertNotNull(outcome);
        assertEquals("sar-filed", outcome.type());
    }

    @Test
    void returns_gate_rejected_for_rejected_human_entry() {
        final AmlSarOfficerReviewedLedgerEntry entry = officerEntry("REJECTED", ActorType.HUMAN);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(entry));
        final InvestigationOutcome outcome = service.resolveOutcome(UUID.randomUUID());
        assertNotNull(outcome);
        assertEquals("gate-rejected", outcome.type());
    }

    @Test
    void returns_decision_not_recorded_for_unknown_system_entry() {
        final AmlSarOfficerReviewedLedgerEntry entry = officerEntry("UNKNOWN", ActorType.SYSTEM);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(entry));
        final InvestigationOutcome outcome = service.resolveOutcome(UUID.randomUUID());
        assertNotNull(outcome);
        assertEquals("decision-not-recorded", outcome.type());
    }

    @Test
    void prefers_human_entry_over_system_entry_in_race() {
        final AmlSarOfficerReviewedLedgerEntry humanEntry = officerEntry("APPROVED", ActorType.HUMAN);
        final AmlSarOfficerReviewedLedgerEntry systemEntry = officerEntry("UNKNOWN", ActorType.SYSTEM);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(humanEntry, systemEntry));
        final InvestigationOutcome outcome = service.resolveOutcome(UUID.randomUUID());
        assertNotNull(outcome);
        assertEquals("sar-filed", outcome.type());
    }

    @Test
    void prefers_human_entry_regardless_of_list_order() {
        final AmlSarOfficerReviewedLedgerEntry humanEntry = officerEntry("REJECTED", ActorType.HUMAN);
        final AmlSarOfficerReviewedLedgerEntry systemEntry = officerEntry("UNKNOWN", ActorType.SYSTEM);
        // SYSTEM entry first in list
        final AmlInvestigationOutcomeService service = serviceWith(List.of(systemEntry, humanEntry));
        final InvestigationOutcome outcome = service.resolveOutcome(UUID.randomUUID());
        assertNotNull(outcome);
        assertEquals("gate-rejected", outcome.type());
    }

    @Test
    void resolveInvestigation_cache_hit_completed_returns_completed_with_outcome() {
        final UUID caseId = UUID.randomUUID();
        final AmlSarOfficerReviewedLedgerEntry entry = officerEntry("APPROVED", ActorType.HUMAN);
        final CaseInstance instance = completedInstance(caseId);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(entry), instance, null);
        final Optional<InvestigationResolution> result = service.resolveInvestigation(caseId);
        assertTrue(result.isPresent());
        assertEquals(InvestigationStatus.COMPLETED, result.get().status());
        assertNotNull(result.get().outcome());
        assertEquals("sar-filed", result.get().outcome().type());
    }

    @Test
    void resolveInvestigation_cache_hit_not_completed_returns_in_progress() {
        final UUID caseId = UUID.randomUUID();
        final CaseInstance instance = inProgressInstance(caseId);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(), instance, null);
        final Optional<InvestigationResolution> result = service.resolveInvestigation(caseId);
        assertTrue(result.isPresent());
        assertEquals(InvestigationStatus.IN_PROGRESS, result.get().status());
        assertNull(result.get().outcome());
    }

    @Test
    void resolveInvestigation_cache_miss_repo_hit_returns_completed() {
        final UUID caseId = UUID.randomUUID();
        final AmlSarOfficerReviewedLedgerEntry entry = officerEntry("REJECTED", ActorType.HUMAN);
        final CaseInstance instance = completedInstance(caseId);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(entry), null, instance);
        final Optional<InvestigationResolution> result = service.resolveInvestigation(caseId);
        assertTrue(result.isPresent());
        assertEquals(InvestigationStatus.COMPLETED, result.get().status());
        assertEquals("gate-rejected", result.get().outcome().type());
    }

    @Test
    void resolveInvestigation_cache_miss_repo_miss_returns_empty() {
        final UUID caseId = UUID.randomUUID();
        final AmlInvestigationOutcomeService service = serviceWith(List.of(), null, null);
        final Optional<InvestigationResolution> result = service.resolveInvestigation(caseId);
        assertTrue(result.isEmpty());
    }

    @Test
    void prefers_higher_sequenceNumber_among_human_entries() {
        final AmlSarOfficerReviewedLedgerEntry older = officerEntry("REJECTED", ActorType.HUMAN);
        older.sequenceNumber = 3;
        final AmlSarOfficerReviewedLedgerEntry newer = officerEntry("APPROVED", ActorType.HUMAN);
        newer.sequenceNumber = 7;
        final AmlInvestigationOutcomeService service = serviceWith(List.of(older, newer));
        final InvestigationOutcome outcome = service.resolveOutcome(UUID.randomUUID());
        assertNotNull(outcome);
        assertEquals("sar-filed", outcome.type());
    }

    @Test
    void system_only_rejected_returns_gate_rejected() {
        final AmlSarOfficerReviewedLedgerEntry entry = officerEntry("REJECTED", ActorType.SYSTEM);
        final AmlInvestigationOutcomeService service = serviceWith(List.of(entry));
        final InvestigationOutcome outcome = service.resolveOutcome(UUID.randomUUID());
        assertNotNull(outcome);
        assertEquals("gate-rejected", outcome.type());
    }

    private static CaseInstance completedInstance(final UUID caseId) {
        final CaseInstance instance = new CaseInstance();
        instance.setUuid(caseId);
        instance.setState(CaseStatus.COMPLETED);
        return instance;
    }

    private static CaseInstance inProgressInstance(final UUID caseId) {
        final CaseInstance instance = new CaseInstance();
        instance.setUuid(caseId);
        instance.setState(CaseStatus.RUNNING);
        return instance;
    }

    private static AmlSarOfficerReviewedLedgerEntry officerEntry(final String decision,
            final ActorType actorType) {
        final AmlSarOfficerReviewedLedgerEntry entry = new AmlSarOfficerReviewedLedgerEntry();
        entry.reviewDecision = decision;
        entry.actorType = actorType;
        return entry;
    }

    private static AmlInvestigationOutcomeService serviceWith(final List<LedgerEntry> entries) {
        return serviceWith(entries, null, null);
    }

    private static AmlInvestigationOutcomeService serviceWith(
            final List<LedgerEntry> entries,
            final CaseInstance cacheResult,
            final CaseInstance repoResult) {
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
        final CaseInstanceCache cache = new CaseInstanceCache() {
            @Override
            public void put(CaseInstance caseInstance) {}

            @Override
            public CaseInstance get(UUID caseId) {
                return cacheResult;
            }

            @Override
            public void clear() {}

            @Override
            public List<CaseInstance> getAll() {
                return List.of();
            }
        };
        final CaseInstanceRepository caseRepo = new CaseInstanceRepository() {
            @Override
            public Uni<CaseInstance> findByUuid(UUID uuid, String tenancyId) {
                return Uni.createFrom().item(repoResult);
            }

            @Override
            public Uni<CaseInstance> save(CaseInstance i, String t) {
                return Uni.createFrom().nullItem();
            }

            @Override
            public Uni<CaseInstance> update(CaseInstance i, String t) {
                return Uni.createFrom().nullItem();
            }

            @Override
            public Uni<Void> updateStateAndAppendEvent(CaseInstance i, EventLog e, String t) {
                return Uni.createFrom().voidItem();
            }
        };
        return new AmlInvestigationOutcomeService(repo, cache, caseRepo);
    }
}
