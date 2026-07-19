package io.casehub.aml.cbr;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.aml.domain.CaseProfile;
import io.casehub.aml.domain.EntityType;
import io.casehub.aml.domain.JurisdictionRisk;
import io.casehub.aml.domain.NetworkComplexity;
import io.casehub.aml.domain.SuspiciousTransaction;
import io.casehub.aml.engine.SarOutcomeRecordedEvent;
import io.casehub.aml.ledger.AmlCaseProfileLedgerEntry;
import io.casehub.aml.ledger.AmlSarOfficerReviewedLedgerEntry;
import io.casehub.aml.memory.AmlMemoryDomains;
import io.casehub.api.model.TaskStatus;
import io.casehub.engine.common.internal.model.PlanItemRecord;
import io.casehub.engine.common.spi.PlanItemStore;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class AmlCaseProfileStoreObserver {

    private static final Logger LOG = Logger.getLogger(AmlCaseProfileStoreObserver.class);

    @Inject
    CbrCaseMemoryStore    cbrStore;
    @Inject
    LedgerEntryRepository ledgerRepository;
    @Inject
    CaseInstanceCache     caseInstanceCache;
    @Inject
    PlanItemStore         planItemStore;
    @Inject
    ObjectMapper          objectMapper;
    @Inject
    CurrentPrincipal      principal;

    @Transactional(TxType.REQUIRES_NEW)
    public void onSarOutcome(@Observes final SarOutcomeRecordedEvent event) {
        var caseId  = event.caseId();
        var outcome = event.outcome();
        var tenantId = principal.tenancyId() != null
                       ? principal.tenancyId()
                       : TenancyConstants.DEFAULT_TENANT_ID;

        var instance = caseInstanceCache.get(caseId);
        if (instance == null) {
            LOG.warnf("No CaseInstance found for caseId=%s — skipping CBR profile store", caseId);
            return;
        }
        var ctx = instance.getCaseContext();

        SuspiciousTransaction tx;
        try {
            tx = objectMapper.convertValue(ctx.get("transaction"), SuspiciousTransaction.class);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to deserialize transaction from case context for caseId=%s — skipping", caseId);
            return;
        }

        var entityTypeStr      = ctx.getString("entityType");
        var jurisdictionStr    = ctx.getString("jurisdictionRisk");
        var networkStr         = ctx.getString("networkComplexity");
        var entityRiskCountObj = ctx.getPath("priorEntityContext.entityRiskCount");
        int priorIncidentCount = entityRiskCountObj instanceof Number n ? n.intValue() : 0;

        EntityType        entityType   = null;
        JurisdictionRisk  jurisdiction = null;
        NetworkComplexity network      = null;
        try {
            if (entityTypeStr != null) {entityType = EntityType.valueOf(entityTypeStr);}
            if (jurisdictionStr != null) {jurisdiction = JurisdictionRisk.valueOf(jurisdictionStr);}
            if (networkStr != null) {network = NetworkComplexity.valueOf(networkStr);}
        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid enrichment value in case context for caseId=%s — using partial profile", caseId);
            entityType   = null;
            jurisdiction = null;
            network      = null;
        }

        CaseProfile profile;
        if (entityType != null && jurisdiction != null && network != null) {
            profile = CaseProfile.complete(
                    tx.flagReason(), tx.amount(), priorIncidentCount,
                    entityType, jurisdiction, network);
        } else {
            profile = CaseProfile.initial(tx.flagReason(), tx.amount(), priorIncidentCount);
        }

        var records = planItemStore.findByCaseId(caseId, tenantId);
        String path = records.stream()
                             .filter(r -> r.status() == TaskStatus.COMPLETED || r.status() == TaskStatus.FAULTED)
                             .filter(r -> r.executorName() != null)
                             .sorted(Comparator.comparing(PlanItemRecord::createdAt))
                             .map(PlanItemRecord::bindingName)
                             .collect(Collectors.joining(" → "));
        if (path.isBlank()) {
            path = "(direct-verdict)";
        }

        String problem = String.format("Flagged transaction %s: %s from %s to %s, amount %s %s",
                                       tx.id(), tx.flagReason().name(),
                                       tx.originAccountId(), tx.destinationAccountId(),
                                       tx.amount().toPlainString(), tx.currency());

        var features     = new LinkedHashMap<>(profile.toFeatures());
        var sarNarrative = ctx.getString("sarNarrative");
        if (sarNarrative != null) {
            features.put("sar_narrative", FeatureValue.string(sarNarrative));
        }

        var cbrCase = new FeatureVectorCbrCase(
                problem, path, outcome.verdict().name(),
                outcome.investigationAccuracyScore(), features);

        String entityId = UUID.nameUUIDFromBytes(
                ("aml-cbr:" + caseId).getBytes(StandardCharsets.UTF_8)).toString();

        try {
            cbrStore.store(cbrCase, AmlCbrSchema.CASE_TYPE, entityId,
                           AmlMemoryDomains.CBR, tenantId, caseId.toString(), Path.root());
            LOG.infof("CBR case profile stored: caseId=%s outcome=%s", caseId, outcome.verdict());
        } catch (Exception e) {
            LOG.warnf(e, "CBR store failed for caseId=%s — skipping", caseId);
        }

        try {
            var entry = new AmlCaseProfileLedgerEntry();
            entry.flagReason         = tx.flagReason().name();
            entry.transactionAmount  = tx.amount();
            entry.priorIncidentCount = priorIncidentCount;
            entry.entityType         = entityTypeStr;
            entry.jurisdictionRisk   = jurisdictionStr;
            entry.networkComplexity  = networkStr;
            entry.outcome            = outcome.verdict().name();
            entry.confidence         = outcome.investigationAccuracyScore();
            entry.investigationPath  = path;
            entry.subjectId          = caseId;
            entry.actorId            = "aml-system";
            entry.tenancyId          = tenantId;
            entry.entryType          = LedgerEntryType.ATTESTATION;

            var existingEntries = ledgerRepository.findBySubjectId(caseId, tenantId);
            existingEntries.stream()
                           .filter(AmlSarOfficerReviewedLedgerEntry.class::isInstance)
                           .max(Comparator.comparing(e -> e.occurredAt != null ? e.occurredAt : Instant.EPOCH))
                           .ifPresent(sarEntry -> entry.causedByEntryId = sarEntry.id);

            ledgerRepository.save(entry, tenantId);
            LOG.infof("CBR profile ledger entry written: caseId=%s", caseId);
        } catch (Exception e) {
            LOG.warnf(e, "Ledger write failed for CBR profile caseId=%s — skipping", caseId);
        }
    }
}
