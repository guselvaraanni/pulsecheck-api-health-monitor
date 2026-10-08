package com.pulsecheck.dto;

import java.time.Instant;
import java.util.List;

/**
 * Metrics for all services over one time window, worst availability first.
 * {@code overallAvailabilityPercent} is calculated from all checks of all services, and is null
 * when there were no checks in the window.
 */
public record MetricsOverviewResponse(
        Instant from,
        Instant to,
        int totalServices,
        long totalChecks,
        Double overallAvailabilityPercent,
        List<ServiceMetricsResponse> services) {
}
