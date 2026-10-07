package com.pulsecheck.dto;

import com.pulsecheck.entity.HealthCheck;
import com.pulsecheck.entity.HealthStatus;

import java.time.Instant;

public record HealthCheckResponse(
        Long id,
        HealthStatus status,
        Long responseTimeMs,
        Instant checkedAt,
        String errorMessage) {

    public static HealthCheckResponse from(HealthCheck check) {
        return new HealthCheckResponse(
                check.getId(),
                check.getStatus(),
                check.getResponseTimeMs(),
                check.getCheckedAt(),
                check.getErrorMessage());
    }
}
