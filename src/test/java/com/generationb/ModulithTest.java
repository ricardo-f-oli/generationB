package com.generationb;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithTest {

    @Test
    void verifyModulithStructure() {
        ApplicationModules modules = ApplicationModules.of(GenerationBApplication.class);
        modules.verify();
    }
}
