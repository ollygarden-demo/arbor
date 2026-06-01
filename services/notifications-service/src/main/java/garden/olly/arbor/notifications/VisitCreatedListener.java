package garden.olly.arbor.notifications;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class VisitCreatedListener {
    private static final Logger log = LoggerFactory.getLogger(VisitCreatedListener.class);
    private final Counter processedCounter;

    public VisitCreatedListener(MeterRegistry registry) {
        this.processedCounter = Counter.builder("notifications.visit.processed")
                .description("Number of visit.created events processed")
                .register(registry);
    }

    @KafkaListener(topics = "visit.created", groupId = "notifications")
    public void onVisitCreated(String payload) {
        log.info("notify: {}", payload);
        processedCounter.increment();
    }

    public double processedCount() {
        return processedCounter.count();
    }
}
