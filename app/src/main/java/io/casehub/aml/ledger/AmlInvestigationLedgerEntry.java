package io.casehub.aml.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import io.casehub.ledger.runtime.model.LedgerEntry;

/**
 * Layer 4: AML domain-level ledger entry recording investigation lifecycle events.
 *
 * <p>Extends {@link LedgerEntry} via JOINED inheritance. The base table
 * ({@code ledger_entry}) holds the tamper-evident Merkle chain fields;
 * this join table adds AML-specific context.
 *
 * <p>Two event types are recorded:
 * <ul>
 * <li>{@code CASE_OPENED} — investigation started</li>
 * <li>{@code COMPLIANCE_REVIEW_OPENED} — SAR review WorkItem created</li>
 * </ul>
 *
 * <p>subjectId on these entries equals the case UUID, shared with qhorus
 * MessageLedgerEntry records for the same investigation (via subjectId
 * propagation in the MessageDispatch API — qhorus#184).
 */
@Entity
@Table(name = "aml_investigation_ledger_entry")
@DiscriminatorValue("AML_INVESTIGATION")
public class AmlInvestigationLedgerEntry extends LedgerEntry {

    /**
     * Context reference for this event. For CASE_OPENED: the external transaction ID
     * being investigated (e.g. "TXN-2024-001"). For COMPLIANCE_REVIEW_OPENED: the
     * WorkItem task ID of the SAR review task.
     * Layer 5+ should consider a dedicated column per event type if this dual use
     * becomes confusing.
     */
    @Column(name = "transaction_id", nullable = false, length = 255)
    public String transactionId;

    /**
     * The lifecycle event: {@code CASE_OPENED} or {@code COMPLIANCE_REVIEW_OPENED}.
     */
    @Column(name = "event_type", nullable = false, length = 50)
    public String eventType;
}
