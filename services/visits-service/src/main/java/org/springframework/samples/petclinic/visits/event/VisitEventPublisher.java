package org.springframework.samples.petclinic.visits.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class VisitEventPublisher {
    private static final String TOPIC = "visit.created";
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;

    public VisitEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    public void publish(VisitCreatedEvent event) {
        try {
            kafka.send(TOPIC, String.valueOf(event.id()), json.writeValueAsString(event));
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize visit event", e);
        }
    }
}
