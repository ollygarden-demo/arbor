package garden.olly.arbor.notifications;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest(properties = {
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "management.zipkin.tracing.export.enabled=false"
})
@EmbeddedKafka(partitions = 1, topics = { "visit.created" })
class VisitCreatedListenerTest {
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired VisitCreatedListener listener;
    @Autowired RecordingObservationHandler observations;

    @Test
    void processesVisitCreatedEvents() {
        kafka.send("visit.created", "1", "{\"id\":1,\"petId\":42,\"description\":\"checkup\"}");
        await().atMost(Duration.ofSeconds(10))
               .untilAsserted(() -> assertThat(listener.processedCount()).isEqualTo(1));
        assertThat(observations.names()).contains("spring.kafka.listener");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ObservationTestConfig {
        @Bean
        RecordingObservationHandler recordingObservationHandler() {
            return new RecordingObservationHandler();
        }
    }

    static class RecordingObservationHandler implements ObservationHandler<Observation.Context> {
        private final List<String> names = new CopyOnWriteArrayList<>();

        @Override
        public void onStart(Observation.Context context) {
            names.add(context.getName());
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        List<String> names() {
            return names;
        }
    }
}
