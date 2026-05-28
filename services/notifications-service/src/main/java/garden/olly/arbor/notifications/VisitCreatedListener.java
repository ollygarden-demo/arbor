package garden.olly.arbor.notifications;

import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class VisitCreatedListener {
    private static final Logger log = LoggerFactory.getLogger(VisitCreatedListener.class);
    private final AtomicInteger processed = new AtomicInteger(0);

    @KafkaListener(topics = "visit.created", groupId = "notifications")
    public void onVisitCreated(String payload) {
        log.info("notify: {}", payload);
        processed.incrementAndGet();
    }

    public int processedCount() {
        return processed.get();
    }
}
