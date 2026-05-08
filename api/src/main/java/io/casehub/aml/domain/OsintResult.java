package io.casehub.aml.domain;

public record OsintResult(boolean sanctionsHit, boolean pepHit, String detail) {}
