package com.pulsecheck.dto;

import com.pulsecheck.entity.MonitoredService;

import java.time.Instant;

public record ServiceResponse(
        Long id,
        String name,
        String url,
        String description,
        boolean active,
        Instant createdAt) {

    public static ServiceResponse from(MonitoredService service) {
        return new ServiceResponse(
                service.getId(),
                service.getName(),
                service.getUrl(),
                service.getDescription(),
                service.isActive(),
                service.getCreatedAt());
    }
}
