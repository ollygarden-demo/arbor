package garden.olly.arbor.notifications;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest(properties = {
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@EmbeddedKafka(partitions = 1, topics = { "visit.created" })
class VisitCreatedListenerTest {
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired VisitCreatedListener listener;

    @Test
    void processesVisitCreatedEvents() {
        kafka.send("visit.created", "1", "{\"id\":1,\"petId\":42,\"description\":\"checkup\"}");
        await().atMost(Duration.ofSeconds(10))
               .untilAsserted(() -> assertThat(listener.processedCount()).isEqualTo(1));
    }
}
