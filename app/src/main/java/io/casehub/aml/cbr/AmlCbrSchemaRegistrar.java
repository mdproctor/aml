package io.casehub.aml.cbr;

import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class AmlCbrSchemaRegistrar {

    @Inject
    CbrCaseMemoryStore cbrStore;

    void onStart(@Observes StartupEvent ev) {
        cbrStore.registerSchema(AmlCbrSchema.SCHEMA);
    }
}
