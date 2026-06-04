package io.casehub.aml.memory;

import io.casehub.platform.api.memory.Memory;
import io.casehub.platform.api.memory.MemoryAttributeKeys;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record AmlPriorContext(
    List<Memory> entityRisk,
    List<Memory> network,
    List<Memory> pattern
) {
    public static AmlPriorContext empty() {
        return new AmlPriorContext(List.of(), List.of(), List.of());
    }

    public boolean hasHistory() {
        return !entityRisk.isEmpty() || !network.isEmpty() || !pattern.isEmpty();
    }

    /**
     * Groups entity-risk memories by entityId, takes the most recent per entity,
     * returns true if any has confidence >= 0.8.
     * A WITHDRAWN reversal (confidence 0.0) supersedes an older UPHELD (0.9).
     */
    public boolean isKnownHighRisk() {
        return entityRisk.stream()
            .collect(Collectors.groupingBy(
                Memory::entityId,
                Collectors.maxBy(Comparator.comparing(Memory::createdAt))))
            .values().stream()
            .filter(Optional::isPresent)
            .map(Optional::get)
            .anyMatch(m -> {
                String conf = m.attributes().get(MemoryAttributeKeys.CONFIDENCE);
                if (conf == null) return false;
                return MemoryAttributeKeys.parseConfidence(conf) >= 0.8;
            });
    }

    /**
     * Serializes to Map<String, Object> for engine initialContext injection.
     * Facts are structured objects (domain, text, createdAt, confidence), not plain strings.
     * Selection: guarantee at least one per non-empty domain, fill to 10 by recency.
     */
    public Map<String, Object> toContextMap() {
        List<Memory> selected = selectFacts(10);
        List<Map<String, Object>> facts = selected.stream()
            .map(m -> {
                Map<String, Object> fact = new LinkedHashMap<>();
                fact.put("domain",     m.domain().name());
                fact.put("text",       m.text());
                fact.put("createdAt",  m.createdAt().toString());
                fact.put("confidence", m.attributes().get(MemoryAttributeKeys.CONFIDENCE));
                return (Map<String, Object>) fact;
            })
            .toList();

        return Map.of(
            "hasHistory",      hasHistory(),
            "knownHighRisk",   isKnownHighRisk(),
            "entityRiskCount", entityRisk.size(),
            "networkCount",    network.size(),
            "patternCount",    pattern.size(),
            "facts",           facts
        );
    }

    private List<Memory> selectFacts(int maxTotal) {
        List<Memory> result = new ArrayList<>();
        Set<String> selectedIds = new HashSet<>();

        // Guarantee at least one per non-empty domain (most recent)
        Stream.of(entityRisk, network, pattern)
            .filter(domain -> !domain.isEmpty())
            .forEach(domain -> domain.stream()
                .max(Comparator.comparing(Memory::createdAt))
                .ifPresent(m -> {
                    result.add(m);
                    selectedIds.add(m.memoryId());
                }));

        // Fill remaining by recency
        List<Memory> all = new ArrayList<>(entityRisk);
        all.addAll(network);
        all.addAll(pattern);
        all.stream()
            .sorted(Comparator.comparing(Memory::createdAt).reversed())
            .filter(m -> !selectedIds.contains(m.memoryId()))
            .limit(Math.max(0L, (long) maxTotal - result.size()))
            .forEach(result::add);

        result.sort(Comparator.comparing(Memory::createdAt).reversed());
        return result.size() <= maxTotal ? result : result.subList(0, maxTotal);
    }
}
