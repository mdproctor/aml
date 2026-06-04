package io.casehub.aml.memory;

import io.casehub.platform.api.memory.MemoryDomain;

public final class AmlMemoryDomains {
    public static final MemoryDomain ENTITY_RISK = new MemoryDomain("aml.entity-risk");
    public static final MemoryDomain NETWORK     = new MemoryDomain("aml.network");
    public static final MemoryDomain PATTERN     = new MemoryDomain("aml.pattern");
    private AmlMemoryDomains() {}
}
