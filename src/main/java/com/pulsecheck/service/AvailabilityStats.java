package com.pulsecheck.service;

import com.pulsecheck.entity.HealthCheck;
import com.pulsecheck.entity.HealthStatus;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Availability numbers for a set of checks.
 * {@code availabilityPercent} and {@code averageResponseTimeMs} are null when there is nothing to
 * calculate them from, because 0 would wrongly mean "always down" or "instant responses".
 */
public record AvailabilityStats(
        long totalChecks,
        long successfulChecks,
        long failedChecks,
        Double availabilityPercent,
        Long averageResponseTimeMs) {

    public static AvailabilityStats from(List<HealthCheck> checks) {
        long total = checks.size();
        long successful = checks.stream()
                .filter(check -> check.getStatus() == HealthStatus.UP)
                .count();

        // Average over successful checks only; failed checks have no meaningful response time.
        OptionalDouble average = checks.stream()
                .filter(check -> check.getStatus() == HealthStatus.UP)
                .map(HealthCheck::getResponseTimeMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average();

        return new AvailabilityStats(
                total,
                successful,
                total - successful,
                percentage(successful, total),
                average.isPresent() ? Math.round(average.getAsDouble()) : null);
    }

    /**
     * {@code part / total * 100}, rounded to 2 decimals, or null when {@code total} is 0.
     */
    public static Double percentage(long part, long total) {
        if (total == 0) {
            return null;
        }
        // Multiply as double first: with longs, 96 / 100 would be 0 (integer division).
        double percent = part * 100.0 / total;
        return Math.round(percent * 100) / 100.0;
    }
}
