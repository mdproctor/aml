package io.casehub.aml.memory;

import io.casehub.memory.MemoryDomain;

/**
 * {@link io.casehub.memory.MemoryDomain} constants for the AML investigation domain.
 *
 * <p>Domain isolation ensures targeted queries and scoped GDPR erasure: querying one domain
 * never returns facts from another, and {@code erase()} calls can be scoped to a single domain.
 *
 * <ul>
 *   <li>{@link #ENTITY_RISK} — risk classification per account, SAR filing history, and outcome
 *       reversals. Used by {@link AmlPriorContext#isKnownHighRisk()} to drive early senior-analyst
 *       routing.
 *   <li>{@link #NETWORK} — counterparty relationship graph between accounts, established during
 *       entity resolution.
 *   <li>{@link #PATTERN} — typology matches (layering, structuring, smurfing) detected during
 *       pattern analysis.
 * </ul>
 *
 * @see AmlMemoryService
 */
public final class AmlMemoryDomains {

    /** Entity risk classification per account — SAR history, risk score, UPHELD/WITHDRAWN outcomes. */
    public static final MemoryDomain ENTITY_RISK = new MemoryDomain("aml.entity-risk");

    /** Counterparty relationship graph — accounts connected through transactions. */
    public static final MemoryDomain NETWORK     = new MemoryDomain("aml.network");

    /** Typology match findings — layering, structuring, smurfing pattern detection. */
    public static final MemoryDomain PATTERN     = new MemoryDomain("aml.pattern");

    private AmlMemoryDomains() {}
}
