package com.siose.ff.logistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.siose.ff.contracts.EventContract;
import com.siose.ff.logistics.routing.RouteStopCompleted;
import com.siose.ff.logistics.transportation.ShipmentDelivered;
import com.siose.ff.logistics.transportation.ShipmentProgressed;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The modulith's events leave the JVM too (S6): the ones marked {@code @Externalized} go from this
 * process's outbox (schema {@code modulith}) to the same Kafka topics the services send to, keyed
 * the same way. One shipment picked up, departed, and delivered by a completed route stop gives a
 * message on each of the three topics; each is checked against its contract (ff-contracts), the
 * check the services' tests run on what they send. The broker runs inside the test JVM.
 */
@SpringBootTest(properties = {
        "spring.modulith.events.externalization.enabled=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.admin.auto-create=true",
        // A database of its own: this context's outbox rows must not mix with the other contexts'.
        "spring.datasource.url=jdbc:h2:mem:logistics_kafka;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc
@EmbeddedKafka
@DirtiesContext
class EventsToKafkaTest {

    private static final String TENANT = "t-kafka-modulith";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private JdbcTemplate jdbc;

    private static RequestPostProcessor caller() {
        return jwt().jwt(token -> token.subject("planner1").claim("tenant_id", TENANT))
                .authorities(new SimpleGrantedAuthority("ROLE_LOGISTICS_PLANNER"),
                        new SimpleGrantedAuthority("ROLE_DISPATCHER"));
    }

    private String json(MockHttpServletRequestBuilder request, String body, int expected) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body).with(caller()))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void everyAreasEventReachesItsTopicAndKeepsItsContract() throws Exception {
        // No destination warehouse and no order reference: the contract allows both to be null.
        String shipment = json(post("/api/v1/tms/shipments"), """
                {"consignorName": "FF Hadimkoy", "consigneeName": "Esenyurt Depot Shop",
                 "equipmentCode": "CSQU3054383", "sscc": "106141411234567897", "incoterm": "DAP",
                 "origin": "Hadimkoy Hub", "destination": "Esenyurt", "grossWeightKg": 800,
                 "legs": [{"mode": "ROAD", "origin": "Hadimkoy Hub", "destination": "Esenyurt",
                           "distanceKm": 9.87, "carrierReference": "TR-34-FF-0417"}]}
                """, 201);
        String shipmentId = JsonPath.read(shipment, "$.id");
        String shipmentNumber = JsonPath.read(shipment, "$.shipmentNumber");
        json(post("/api/v1/tms/shipments/" + shipmentId + "/pickup"), "", 200);
        json(post("/api/v1/tms/shipments/" + shipmentId + "/departure"), "", 200);

        json(post("/api/v1/geo/territories"), """
                {"territoryCode": "IST-EU", "name": "Istanbul Europe", "hubGln": "8690001000011",
                 "centerLatitude": 41.05, "centerLongitude": 28.85, "coverageRadiusKm": 35,
                 "activePointCount": 420, "weeklyVisitTarget": 840}
                """, 201);
        String route = json(post("/api/v1/geo/routes"), """
                {"territoryCode": "IST-EU", "vehiclePlate": "34 FF 0417", "depotLatitude": 41.10,
                 "depotLongitude": 28.65, "vehicleCapacityKg": 3500, "averageSpeedKmh": 40, "stops": [
                  {"stopCode": "IST-ESN", "customerName": "Esenyurt Depot Shop", "latitude": 41.03,
                   "longitude": 28.67, "demandWeightKg": 200, "serviceMinutes": 15, "shipmentNumber": "%s"}]}
                """.formatted(shipmentNumber), 201);
        String routeId = JsonPath.read(route, "$.id");
        // Flow 3, in this JVM: the completed stop delivers the shipment, which publishes ShipmentDelivered.
        json(post("/api/v1/geo/routes/" + routeId + "/stops/IST-ESN/completion"),
                "{\"receivedBy\": \"Ayse Demir\"}", 200);

        ConsumerRecord<String, String> stop = single(RouteStopCompleted.TOPIC);
        assertThat(stop.key()).isEqualTo(routeId);
        EventContract.of(RouteStopCompleted.TOPIC).verify(stop.key(), stop.value());

        ConsumerRecord<String, String> delivered = single(ShipmentDelivered.TOPIC);
        assertThat(delivered.key()).isEqualTo(shipmentId);
        assertThat((String) JsonPath.read(delivered.value(), "$.tenantId")).isEqualTo(TENANT);
        EventContract.of(ShipmentDelivered.TOPIC).verify(delivered.key(), delivered.value());

        ConsumerRecords<String, String> progressed = records(ShipmentProgressed.TOPIC, 2);
        assertThat(progressed.count()).isEqualTo(2);
        progressed.forEach(record -> EventContract.of(ShipmentProgressed.TOPIC).verify(record.key(), record.value()));

        // Sent, so every outbox row of this shipment and this route is completed.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "select count(*) from modulith.event_publication where completion_date is null"
                        + " and (serialized_event like ? or serialized_event like ?)", Integer.class,
                "%" + shipmentId + "%", "%" + routeId + "%")).isZero());
    }

    private ConsumerRecord<String, String> single(String topic) {
        try (Consumer<String, String> consumer = consumer(topic)) {
            return KafkaTestUtils.getSingleRecord(consumer, topic, Duration.ofSeconds(15));
        }
    }

    private ConsumerRecords<String, String> records(String topic, int count) {
        try (Consumer<String, String> consumer = consumer(topic)) {
            return KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(15), count);
        }
    }

    private Consumer<String, String> consumer(String topic) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "test-reader-" + topic, false);
        props.put("auto.offset.reset", "earliest");
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new StringDeserializer()).createConsumer();
        consumer.subscribe(List.of(topic));
        return consumer;
    }
}
