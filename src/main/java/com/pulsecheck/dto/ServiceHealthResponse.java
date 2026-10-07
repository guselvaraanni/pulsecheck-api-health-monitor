package com.pulsecheck.dto;

import java.time.Instant;

/**
 * Current health of a service, based on its most recent check.
 * {@code status} is "UP", "DOWN", or "UNKNOWN" when the service has never been checked.
 */
public record ServiceHealthResponse(
        Long serviceId,
        String serviceName,
        String status,
        Instant lastCheckedAt,
        Long responseTimeMs,
        String errorMessage) {
}
