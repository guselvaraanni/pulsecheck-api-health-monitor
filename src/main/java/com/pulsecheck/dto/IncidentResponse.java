package com.pulsecheck.dto;

import com.pulsecheck.entity.Incident;

import java.time.Duration;
import java.time.Instant;

/**
 * {@code durationSeconds} is measured up to now for incidents that are still open.
 */
public record IncidentResponse(
        Long id,
        Long serviceId,
        String serviceName,
        Instant startedAt,
        Instant endedAt,
        boolean open,
        long durationSeconds,
        String reason) {

    public static IncidentResponse from(Incident incident) {
        Instant end = incident.isOpen() ? Instant.now() : incident.getEndedAt();
        return new IncidentResponse(
                incident.getId(),
                incident.getService().getId(),
                incident.getService().getName(),
                incident.getStartedAt(),
                incident.getEndedAt(),
                incident.isOpen(),
                Duration.between(incident.getStartedAt(), end).toSeconds(),
                incident.getReason());
    }
}
