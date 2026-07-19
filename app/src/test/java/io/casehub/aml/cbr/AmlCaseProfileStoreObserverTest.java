package io.casehub.aml.cbr;

import io.casehub.aml.domain.FlagReason;
import io.casehub.aml.domain.SarOutcome;
import io.casehub.aml.domain.SarVerdict;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.engine.AmlEngineCoordinator;
import io.casehub.aml.engine.SarOutcomeRecordedEvent;
import io.casehub.aml.ledger.AmlCaseProfileLedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.service.WorkItemService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AmlCaseProfileStoreObserverTest {

    private static final String TENANT = TenancyConstants.DEFAULT_TENANT_ID;
    @Inject
    AmlEngineCoordinator           coordinator;
    @Inject
    Event<SarOutcomeRecordedEvent> sarOutcomeEvent;
    @Inject
    CbrCaseMemoryStore             cbrStore;
    @Inject
    LedgerEntryRepository          ledgerRepository;
    @Inject
    WorkItemService                workItemService;
    @PersistenceContext
    EntityManager defaultEm;

    private List<WorkItem> findGateWorkItems(final UUID caseId) {
        return QuarkusTransaction.requiringNew().call(() ->
                                                              defaultEm.createQuery(
                                                                               "SELECT w FROM WorkItem w WHERE w.callerRef LIKE :pattern",
                                                                               WorkItem.class)
                                                                       .setParameter("pattern", "case:" + caseId + "/gate:%")
                                                                       .getResultList());
    }

    private void awaitAndApproveGate(final UUID caseId) {
        Awaitility.await()
                  .atMost(15, TimeUnit.SECONDS)
                  .pollInterval(300, TimeUnit.MILLISECONDS)
                  .until(() -> !findGateWorkItems(caseId).isEmpty());
        final WorkItem gate = findGateWorkItems(caseId).get(0);
        workItemService.completeFromSystem(gate.id, "test-mlro", "approved");
    }

    private void drain(final UUID caseId) {
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100))
                  .until(() -> "completed".equals(
                          given().when().get("/api/layer6/investigations/" + caseId)
                                 .then().extract().path("status")));
    }

    @Test
    void onSarOutcome_storesProfileAndLedgerEntry() {
        SuspiciousTransaction tx = new SuspiciousTransaction(
                "TXN-CBR-001-" + UUID.randomUUID(),
                "ACC-CBR-ORIGIN-" + UUID.randomUUID(),
                "ACC-CBR-DEST-" + UUID.randomUUID(),
                new BigDecimal("75000"), "USD", Instant.now(), FlagReason.STRUCTURING);
        UUID caseId = coordinator.startInvestigation(tx);
        awaitAndApproveGate(caseId);
        drain(caseId);

        sarOutcomeEvent.fire(new SarOutcomeRecordedEvent(
                caseId, new SarOutcome(SarVerdict.UPHELD, "Confirmed structuring", 0.92)));

        var ledgerEntries = ledgerRepository.findBySubjectId(caseId, TENANT);
        var profileEntry = ledgerEntries.stream()
                                        .filter(AmlCaseProfileLedgerEntry.class::isInstance)
                                        .map(AmlCaseProfileLedgerEntry.class::cast)
                                        .findFirst()
                                        .orElse(null);

        assertNotNull(profileEntry, "AmlCaseProfileLedgerEntry must be written");
        assertEquals("STRUCTURING", profileEntry.flagReason);
        assertEquals(0, new BigDecimal("75000").compareTo(profileEntry.transactionAmount));
        assertEquals("UPHELD", profileEntry.outcome);
        assertEquals(0.92, profileEntry.confidence, 0.001);
        assertNotNull(profileEntry.investigationPath);
        assertFalse(profileEntry.investigationPath.isBlank());
    }

    @Test
    void onSarOutcome_cbrStoreContainsCase() {
        SuspiciousTransaction tx = new SuspiciousTransaction(
                "TXN-CBR-002-" + UUID.randomUUID(),
                "ACC-CBR-ORIGIN2-" + UUID.randomUUID(),
                "ACC-CBR-DEST2-" + UUID.randomUUID(),
                new BigDecimal("50000"), "USD", Instant.now(), FlagReason.LAYERING);
        UUID caseId = coordinator.startInvestigation(tx);
        awaitAndApproveGate(caseId);
        drain(caseId);

        sarOutcomeEvent.fire(new SarOutcomeRecordedEvent(
                caseId, new SarOutcome(SarVerdict.FLAGGED, "Suspicious layering", 0.65)));

        var query = CbrQuery.of(TENANT, io.casehub.aml.memory.AmlMemoryDomains.CBR,
                                io.casehub.platform.api.path.Path.root(),
                                AmlCbrSchema.CASE_TYPE,
                                Map.of("flag_reason", FeatureValue.string("LAYERING")), 10)
                            .withWeights(AmlCbrSchema.WEIGHTS);

        var results = cbrStore.retrieveSimilar(query, FeatureVectorCbrCase.class);
        assertTrue(results.stream().anyMatch(r ->
                                                     "FLAGGED".equals(r.cbrCase().outcome())),
                   "CBR store must contain the case with FLAGGED outcome");
    }

    @Test
    void onSarOutcome_noCaseInstance_doesNotThrow() {
        UUID nonexistentCaseId = UUID.randomUUID();
        assertDoesNotThrow(() ->
                                   sarOutcomeEvent.fire(new SarOutcomeRecordedEvent(
                                           nonexistentCaseId, new SarOutcome(SarVerdict.UPHELD, "test", 0.9))));
    }

    @Test
    void onSarOutcome_partialProfile_missingEnrichment() {
        SuspiciousTransaction tx = new SuspiciousTransaction(
                "TXN-CBR-003-" + UUID.randomUUID(),
                "ACC-CBR-PARTIAL-" + UUID.randomUUID(),
                "ACC-CBR-PARTIAL-DEST-" + UUID.randomUUID(),
                new BigDecimal("25000"), "USD", Instant.now(), FlagReason.SMURFING);
        UUID caseId = coordinator.startInvestigation(tx);
        awaitAndApproveGate(caseId);
        drain(caseId);

        sarOutcomeEvent.fire(new SarOutcomeRecordedEvent(
                caseId, new SarOutcome(SarVerdict.WITHDRAWN, "False positive", 0.15)));

        var ledgerEntries = ledgerRepository.findBySubjectId(caseId, TENANT);
        var profileEntry = ledgerEntries.stream()
                                        .filter(AmlCaseProfileLedgerEntry.class::isInstance)
                                        .map(AmlCaseProfileLedgerEntry.class::cast)
                                        .findFirst()
                                        .orElse(null);

        assertNotNull(profileEntry, "Partial profile must still produce a ledger entry");
        assertEquals("SMURFING", profileEntry.flagReason);
        assertEquals("WITHDRAWN", profileEntry.outcome);
        assertNull(profileEntry.entityType, "Enrichment fields null for partial profile");
    }
}
