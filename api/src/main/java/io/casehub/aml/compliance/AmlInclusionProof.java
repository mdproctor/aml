package io.casehub.aml.compliance;

import java.util.List;

public record AmlInclusionProof(
    int entryIndex,
    int treeSize,
    String leafHash,
    List<AmlProofStep> siblings,
    String treeRoot
) {}
