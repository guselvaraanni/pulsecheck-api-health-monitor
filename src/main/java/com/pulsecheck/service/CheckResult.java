package com.pulsecheck.service;

import com.pulsecheck.entity.HealthStatus;

import java.time.Instant;

/**
 * Outcome of one HTTP check, before it is stored.
 * {@code responseTimeMs} is null when no HTTP response was received (timeout, connection failure).
 */
public record CheckResult(HealthStatus status, Long responseTimeMs, Instant checkedAt, String errorMessage) {

    // Matches the length of the health_checks.error_message column.
    private static final int MAX_ERROR_LENGTH = 1000;

    static CheckResult up(long responseTimeMs, Instant checkedAt) {
        return new CheckResult(HealthStatus.UP, responseTimeMs, checkedAt, null);
    }

    static CheckResult down(Long responseTimeMs, Instant checkedAt, String errorMessage) {
        String message = errorMessage.length() > MAX_ERROR_LENGTH
                ? errorMessage.substring(0, MAX_ERROR_LENGTH)
                : errorMessage;
        return new CheckResult(HealthStatus.DOWN, responseTimeMs, checkedAt, message);
    }
}
