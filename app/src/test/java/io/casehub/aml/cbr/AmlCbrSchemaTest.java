package io.casehub.aml.cbr;

import io.casehub.neocortex.memory.cbr.FeatureField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmlCbrSchemaTest {

    @Test
    void schema_hasSixFields() {assertEquals(7, AmlCbrSchema.SCHEMA.fields().size());}

    @Test
    void schema_caseType() {
        assertEquals("aml-investigation", AmlCbrSchema.SCHEMA.caseType());
    }

    @Test
    void schema_fieldNames() {
        var names = AmlCbrSchema.SCHEMA.fields().stream()
                                       .map(FeatureField::name).toList();
        assertTrue(names.contains("flag_reason"));
        assertTrue(names.contains("transaction_amount"));
        assertTrue(names.contains("prior_incident_count"));
        assertTrue(names.contains("entity_type"));
        assertTrue(names.contains("jurisdiction_risk"));
        assertTrue(names.contains("network_complexity"));
        assertTrue(names.contains("sar_narrative"));
    }

    @Test
    void schema_sarNarrativeIsSemanticText() {
        var sarNarrative = AmlCbrSchema.SCHEMA.fields().stream()
                                              .filter(f -> "sar_narrative".equals(f.name()))
                                              .findFirst()
                                              .orElseThrow(() -> new AssertionError("sar_narrative field not found"));

        assertTrue(sarNarrative instanceof FeatureField.Text, "sar_narrative should be a Text field");
        assertTrue(((FeatureField.Text) sarNarrative).semantic(), "sar_narrative should be semantic");
    }


    @Test
    void schema_hasFourCategoricalTwoNumeric() {
        long categoricals = AmlCbrSchema.SCHEMA.fields().stream()
                                               .filter(f -> f instanceof FeatureField.Categorical).count();
        long numerics = AmlCbrSchema.SCHEMA.fields().stream()
                                           .filter(f -> f instanceof FeatureField.Numeric).count();
        long texts = AmlCbrSchema.SCHEMA.fields().stream()
                                        .filter(f -> f instanceof FeatureField.Text).count();
        assertEquals(4, categoricals);
        assertEquals(2, numerics);
        assertEquals(1, texts);
    }

    @Test
    void weights_sumToOne() {
        double sum = AmlCbrSchema.WEIGHTS.values().stream()
                                         .mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sum, 0.001);
    }

    @Test
    void weights_coverAllSchemaFields() {
        for (FeatureField field : AmlCbrSchema.SCHEMA.fields()) {
            if (field instanceof FeatureField.Text) {continue;}
            assertTrue(AmlCbrSchema.WEIGHTS.containsKey(field.name()),
                       "Missing weight for field: " + field.name());
        }
    }
}
