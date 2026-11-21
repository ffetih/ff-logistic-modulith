package com.siose.ff.logistics;

import com.siose.ff.logistics.routing.RouteStopCompleted;
import com.siose.ff.logistics.transportation.ShipmentDelivered;
import com.siose.ff.logistics.transportation.ShipmentProgressed;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

/**
 * The Kafka topics this application writes to (S6): every area's, since all of them run here. The
 * services create the same topics, each its own (ShipmentTopics, RouteStopTopics), with the same
 * partitions: whichever side runs first creates a topic, and the other finds it. Spring's KafkaAdmin
 * creates a missing topic at the start and never shrinks one; the broker creates none by itself.
 *
 * <p>Not in the migration Job ({@code migrate}), which should not wait for a broker, and only when
 * the events are sent at all. In the root package, not a module: it names the areas' events and
 * belongs to none of them.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.modulith.events.externalization.enabled", havingValue = "true")
@Profile("!migrate")
class EventTopics {

    @Bean
    NewTopic shipmentDeliveredTopic(@Value("${ff.kafka.partitions:3}") int partitions,
            @Value("${ff.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(ShipmentDelivered.TOPIC).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    NewTopic shipmentProgressedTopic(@Value("${ff.kafka.partitions:3}") int partitions,
            @Value("${ff.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(ShipmentProgressed.TOPIC).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    NewTopic routeStopCompletedTopic(@Value("${ff.kafka.partitions:3}") int partitions,
            @Value("${ff.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(RouteStopCompleted.TOPIC).partitions(partitions).replicas(replicas).build();
    }
}
