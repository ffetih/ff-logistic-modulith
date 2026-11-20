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
class StopToWarehouseFlowTest {

    private static final String TENANT = "t-flow3-modulith";
    private static final String PREFIX = "8690004";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private static RequestPostProcessor caller() {
        return jwt().jwt(token -> token.subject("admin1").claim("tenant_id", TENANT))
                .authorities(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"),
                        new SimpleGrantedAuthority("ROLE_WAREHOUSE_OPERATOR"),
                        new SimpleGrantedAuthority("ROLE_LOGISTICS_PLANNER"));
    }

    private String json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String body, int expected) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body).with(caller()))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * Flows 3 and 2 in a row: the driver completes a stop, routing publishes it, transportation
     * delivers the shipment the stop carried, and the warehouse at its destination opens an arrival.
     */
    @Test
    void aCompletedStopDeliversItsShipmentAndTheWarehouseOpensAnArrival() throws Exception {
        String gln = PREFIX + "00001" + Gs1Standards.checkDigit(PREFIX + "00001");
        json(post("/api/v1/wms/warehouses"), """
                {"code": "IST-ESN", "name": "Esenyurt Cross-dock", "gln": "%s", "gs1CompanyPrefix": "%s",
                 "countryCode": "TR", "city": "Istanbul", "latitude": 41.03, "longitude": 28.67,
                 "palletCapacity": 200}
                """.formatted(gln, PREFIX), 201);
        String shipment = json(post("/api/v1/tms/shipments"), """
                {"consignorName": "FF Hadimkoy", "consigneeName": "Esenyurt Cross-dock", "destinationGln": "%s",
                 "equipmentCode": "CSQU3054383", "sscc": "106141411234567897", "incoterm": "DAP",
                 "origin": "Hadimkoy Hub", "destination": "Esenyurt", "grossWeightKg": 800,
                 "legs": [{"mode": "ROAD", "origin": "Hadimkoy Hub", "destination": "Esenyurt",
                           "distanceKm": 9.87, "carrierReference": "TR-34-FF-0417"}]}
                """.formatted(gln), 201);
        String shipmentId = JsonPath.read(shipment, "$.id");
        String shipmentNumber = JsonPath.read(shipment, "$.shipmentNumber");
        json(post("/api/v1/geo/territories"), """
                {"territoryCode": "IST-EU", "name": "Istanbul Europe", "hubGln": "%s",
                 "centerLatitude": 41.05, "centerLongitude": 28.85, "coverageRadiusKm": 35,
                 "activePointCount": 420, "weeklyVisitTarget": 840}
                """.formatted(gln), 201);
        String route = json(post("/api/v1/geo/routes"), """
                {"territoryCode": "IST-EU", "vehiclePlate": "34 FF 0417", "depotLatitude": 41.10,
                 "depotLongitude": 28.65, "vehicleCapacityKg": 3500, "averageSpeedKmh": 40, "stops": [
                  {"stopCode": "IST-ESN", "customerName": "Esenyurt Cross-dock", "latitude": 41.03,
                   "longitude": 28.67, "demandWeightKg": 800, "serviceMinutes": 15, "shipmentNumber": "%s"}]}
                """.formatted(shipmentNumber), 201);
        String routeId = JsonPath.read(route, "$.id");

        json(post("/api/v1/geo/routes/" + routeId + "/stops/IST-ESN/completion"), "{\"receivedBy\": \"Dock 1\"}", 200);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            String delivered = mockMvc.perform(get("/api/v1/tms/shipments/" + shipmentId).with(caller()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat((String) JsonPath.read(delivered, "$.status")).isEqualTo("DELIVERED");
            assertThat((String) JsonPath.read(delivered, "$.receivedBy")).isEqualTo("Dock 1");
        });
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            String arrivals = mockMvc.perform(get("/api/v1/wms/arrivals").with(caller()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<String> shipments = JsonPath.read(arrivals, "$[*].shipmentId");
            assertThat(shipments).contains(shipmentId);
        });
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "select count(*) from modulith.event_publication where completion_date is null"
                        + " and (serialized_event like ? or serialized_event like ?)", Integer.class,
                "%" + routeId + "%", "%" + shipmentId + "%")).isZero());
    }
}
