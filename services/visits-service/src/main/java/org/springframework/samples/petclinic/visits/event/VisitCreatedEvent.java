package org.springframework.samples.petclinic.visits.event;

import java.util.Date;

public record VisitCreatedEvent(int id, int petId, Date date, String description) {}
