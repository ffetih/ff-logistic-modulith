package com.siose.ff.logistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.siose.ff.logistics.standards.Gs1Standards;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Flow 2 in the modulith, through the API as a client sees it: a shipment delivered to a warehouse
 * opens an inbound arrival there. Transportation publishes an event; the registry stores it with the
 * delivery, in the schema {@code modulith}; the flow's listener hears it after the commit and opens
 * the arrival; the registry marks the row completed.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeliveryToWarehouseFlowTest {

    private static final String TENANT = "t-flow-modulith";
    private static final String PREFIX = "8690003";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private static RequestPostProcessor caller(String... roles) {
        return jwt().jwt(token -> token.subject("planner1").claim("tenant_id", TENANT))
                .authorities(java.util.Arrays.stream(roles).map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(org.springframework.security.core.GrantedAuthority[]::new));
    }

    private String json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String body, int expected) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(caller("WAREHOUSE_OPERATOR", "LOGISTICS_PLANNER")))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void aDeliveryToAWarehouseOpensAnArrivalThere() throws Exception {
        String gln = PREFIX + "00001" + Gs1Standards.checkDigit(PREFIX + "00001");
        json(post("/api/v1/wms/warehouses"), """
                {"code": "CGN-FLOW", "name": "Cologne Eifeltor Hub", "gln": "%s", "gs1CompanyPrefix": "%s",
                 "countryCode": "DE", "city": "Cologne", "latitude": 50.9, "longitude": 6.9,
                 "palletCapacity": 500}
                """.formatted(gln, PREFIX), 201);
        String shipment = json(post("/api/v1/tms/shipments"), """
                {"consignorName": "Bosch Bursa", "consigneeName": "Rhein Parts GmbH", "destinationGln": "%s",
                 "equipmentCode": "CSQU3054383", "sscc": "106141411234567897", "incoterm": "DAP",
                 "origin": "Istanbul Pendik", "destination": "Cologne Eifeltor", "grossWeightKg": 22000,
                 "legs": [{"mode": "ROAD", "origin": "Istanbul Pendik", "destination": "Cologne Eifeltor",
                           "distanceKm": 2600, "carrierReference": "TR-34-FF-0417"}]}
                """.formatted(gln), 201);
        String id = JsonPath.read(shipment, "$.id");

        json(post("/api/v1/tms/shipments/" + id + "/delivery"), "{\"receivedBy\": \"Gate 3\"}", 200);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            String arrivals = mockMvc.perform(get("/api/v1/wms/arrivals").with(caller("WAREHOUSE_OPERATOR")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<String> shipments = JsonPath.read(arrivals, "$[*].shipmentId");
            assertThat(shipments).containsExactly(id);
        });
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "select count(*) from modulith.event_publication where completion_date is null"
                        + " and serialized_event like ?", Integer.class, "%" + id + "%")).isZero());
    }
}
