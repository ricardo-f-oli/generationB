package com.generationb.outreach;

import com.generationb.GenerationBApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class OutreachModuleModulithTest {

    @Test
    void verifyModuleStructure() {
        ApplicationModules modules = ApplicationModules.of(GenerationBApplication.class);
        modules.getModuleByName("outreach").ifPresent(module -> {
            modules.verify();
        });
    }
}
