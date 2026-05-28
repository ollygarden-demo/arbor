package org.springframework.samples.petclinic.visits.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import java.util.Collections;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@EmbeddedKafka(partitions = 1, topics = { "visit.created" })
class VisitEventPublisherTest {
    @Autowired VisitEventPublisher publisher;
    @Autowired org.springframework.kafka.test.EmbeddedKafkaBroker broker;

    @Test
    void publishesVisitCreatedEvent() {
        publisher.publish(new VisitCreatedEvent(1, 42, new Date(), "checkup"));

        Map<String, Object> props = KafkaTestUtils.consumerProps("g", "false", broker);
        try (Consumer<String, String> c = new org.apache.kafka.clients.consumer.KafkaConsumer<>(
                props, new org.apache.kafka.common.serialization.StringDeserializer(),
                new org.apache.kafka.common.serialization.StringDeserializer())) {
            c.subscribe(Collections.singleton("visit.created"));
            ConsumerRecord<String, String> rec = KafkaTestUtils.getSingleRecord(c, "visit.created");
            assertThat(rec.value()).contains("\"petId\":42");
        }
    }
}
