package com.pulsecheck.dto;

import com.pulsecheck.entity.MonitoredService;
import com.pulsecheck.service.AvailabilityStats;

import java.time.Instant;

/**
 * Availability metrics for one service over the window {@code from}..{@code to}.
 * {@code availabilityPercent} and {@code averageResponseTimeMs} are null when there is no data.
 */
public record ServiceMetricsResponse(
        Long serviceId,
        String serviceName,
        Instant from,
        Instant to,
        long totalChecks,
        long successfulChecks,
        long failedChecks,
        Double availabilityPercent,
        Long averageResponseTimeMs) {

    public static ServiceMetricsResponse of(MonitoredService service, Instant from, Instant to,
                                            AvailabilityStats stats) {
        return new ServiceMetricsResponse(
                service.getId(),
                service.getName(),
                from,
                to,
                stats.totalChecks(),
                stats.successfulChecks(),
                stats.failedChecks(),
                stats.availabilityPercent(),
                stats.averageResponseTimeMs());
    }
}
