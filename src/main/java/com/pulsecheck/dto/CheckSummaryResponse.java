package com.pulsecheck.dto;

import com.pulsecheck.entity.HealthStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Summary of a service's most recent checks.
 * Response-time fields and {@code lastFailureAt} are null when there is no data for them.
 */
public record CheckSummaryResponse(
        Long serviceId,
        String serviceName,
        int checksAnalyzed,
        Map<HealthStatus, Long> statusCounts,
        long failureCount,
        long consecutiveFailures,
        Long fastestResponseMs,
        Long slowestResponseMs,
        Instant lastFailureAt,
        List<ErrorCount> topErrors) {
}
