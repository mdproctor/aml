package io.casehub.aml;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.aml.domain.InvestigationStatus;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;

import io.casehub.aml.domain.SpecialistOutcome;

@Singleton
public class AmlJacksonConfig implements ObjectMapperCustomizer {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = SpecialistOutcome.Completed.class, name = "Completed"),
            @JsonSubTypes.Type(value = SpecialistOutcome.Declined.class,  name = "Declined"),
            @JsonSubTypes.Type(value = SpecialistOutcome.Failed.class,    name = "Failed")
    })
    interface SpecialistOutcomeMixin {}

    interface InvestigationStatusMixin {
        @JsonValue String toWireFormat();
        @JsonCreator static InvestigationStatus fromWireFormat(String value) { return null; }
    }

    @Override
    public void customize(ObjectMapper mapper) {
        mapper.addMixIn(SpecialistOutcome.class, SpecialistOutcomeMixin.class);
        mapper.addMixIn(InvestigationStatus.class, InvestigationStatusMixin.class);
    }
}
