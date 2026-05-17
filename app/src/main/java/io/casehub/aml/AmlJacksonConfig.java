package io.casehub.aml;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Override
    public void customize(ObjectMapper mapper) {
        mapper.addMixIn(SpecialistOutcome.class, SpecialistOutcomeMixin.class);
    }
}
