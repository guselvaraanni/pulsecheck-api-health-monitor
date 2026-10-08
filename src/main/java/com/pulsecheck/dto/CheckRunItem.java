package com.pulsecheck.dto;

import com.pulsecheck.entity.HealthCheck;
import com.pulsecheck.entity.HealthStatus;

import java.time.Instant;

public record CheckRunItem(
        Long serviceId,
        String serviceName,
        HealthStatus status,
        Long responseTimeMs,
        Instant checkedAt,
        String errorMessage) {

    public static CheckRunItem from(HealthCheck check) {
        return new CheckRunItem(
                check.getService().getId(),
                check.getService().getName(),
                check.getStatus(),
                check.getResponseTimeMs(),
                check.getCheckedAt(),
                check.getErrorMessage());
    }
}
