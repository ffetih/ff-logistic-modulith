package com.siose.ff.logistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** The walking skeleton: the areas migrated, the probes, and a token's caller. */
@SpringBootTest
@AutoConfigureMockMvc
class LogisticsApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migratesEachAreaWithItsOwnHistory() {
        Map<String, Integer> expected = Map.of("warehousing", 4, "transportation", 2, "routing", 2);
        expected.forEach((area, latest) -> {
            Integer version = jdbc.queryForObject("select max(\"version\") from \"" + area + "_flyway_history\""
                    + " where \"type\" = 'SQL'", Integer.class);
            assertThat(version).as(area).isEqualTo(latest);
        });
    }

    @Test
    void keepsItsOutboxInASchemaOfItsOwn() {
        Integer version = jdbc.queryForObject("select max(\"version\") from modulith.\"outbox_flyway_history\""
                + " where \"type\" = 'SQL'", Integer.class);
        assertThat(version).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from modulith.event_publication", Integer.class)).isNotNull();
    }

    @Test
    void answersTheProbesAndTheReleaseGroup() throws Exception {
        for (String path : new String[] {"/actuator/health/liveness", "/actuator/health/readiness",
                "/actuator/health/release"}) {
            mockMvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        }
    }

    @Test
    void saysWhoIsCallingOnlyWithAToken() throws Exception {
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token
                        .subject("dispatcher1").claim("tenant_id", "ff-eu-freight"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("ff-eu-freight"))
                .andExpect(jsonPath("$.username").value("dispatcher1"));
    }
}
