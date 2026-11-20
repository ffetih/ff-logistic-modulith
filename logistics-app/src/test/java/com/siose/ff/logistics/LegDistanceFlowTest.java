package com.siose.ff.logistics;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Flow 1 in the modulith, through the API as a client sees it: a road leg given by its ends gets its
 * distance from routing. With the straight-line road matrix (the default) routing's answer equals
 * the estimate, 9.87 km; the leg's distanceSource says who gave it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LegDistanceFlowTest {

    static final String ROAD_BY_ENDS = """
            {"consignorName": "FF Hadimkoy", "consigneeName": "Esenyurt Depot Shop",
             "equipmentCode": "CSQU3054383", "sscc": "106141411234567897", "incoterm": "DAP",
             "origin": "Hadimkoy Hub", "destination": "Esenyurt", "grossWeightKg": 800,
             "legs": [
               {"mode": "ROAD", "origin": "Hadimkoy Hub", "destination": "Esenyurt",
                "originLatitude": 41.10, "originLongitude": 28.65,
                "destinationLatitude": 41.03, "destinationLongitude": 28.67,
                "carrierReference": "TR-34-FF-0417"}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aRoadLegGetsItsDistanceFromRouting() throws Exception {
        mockMvc.perform(post("/api/v1/tms/shipments").contentType(MediaType.APPLICATION_JSON).content(ROAD_BY_ENDS)
                        .with(planner("t-flow1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.legs[0].distanceKm").value(9.87))
                .andExpect(jsonPath("$.legs[0].distanceSource").value("ROUTING"));
    }

    static RequestPostProcessor planner(String tenant) {
        return jwt().jwt(token -> token.subject("planner1").claim("tenant_id", tenant))
                .authorities(new SimpleGrantedAuthority("ROLE_LOGISTICS_PLANNER"));
    }
}
