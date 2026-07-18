package io.casehub.aml.cbr;

import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.inmem.InMemoryCbrCaseMemoryStore;
import io.quarkus.runtime.StartupEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AmlCbrSchemaRegistrarTest {

    @Test
    void onStart_registersSchema_noError() {
        CbrCaseMemoryStore store = new InMemoryCbrCaseMemoryStore();
        AmlCbrSchemaRegistrar registrar = new AmlCbrSchemaRegistrar();
        registrar.cbrStore = store;

        assertDoesNotThrow(() -> registrar.onStart(new StartupEvent()));
    }
}
