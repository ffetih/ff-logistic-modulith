package com.siose.ff.logistics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

/**
 * The business areas, from shared's jars, as Spring Modulith modules: none reaches into another's
 * internals, none uses an area it does not declare, and none forms a cycle (decision 2).
 */
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(LogisticsApplication.class);

    @Test
    void theAreasAreTheModules() {
        assertThat(modules.stream().map(ApplicationModule::getIdentifier).map(Object::toString))
                .containsExactlyInAnyOrder("standards", "warehousing", "transportation", "routing");
    }

    @Test
    void theBoundariesHold() {
        modules.verify();
    }
}
