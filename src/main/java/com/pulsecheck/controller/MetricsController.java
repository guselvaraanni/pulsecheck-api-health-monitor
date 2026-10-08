package com.pulsecheck.controller;

import com.pulsecheck.dto.MetricsOverviewResponse;
import com.pulsecheck.dto.ServiceMetricsResponse;
import com.pulsecheck.service.MetricsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetricsController {

    // Window is capped at 7 days to bound how many checks are loaded per request.
    private static final int MAX_WINDOW_HOURS = 168;

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/api/services/{serviceId}/metrics")
    public ServiceMetricsResponse getServiceMetrics(
            @PathVariable Long serviceId,
            @RequestParam(defaultValue = "24") @Min(1) @Max(MAX_WINDOW_HOURS) int hours) {
        return metricsService.getServiceMetrics(serviceId, hours);
    }

    @GetMapping("/api/metrics")
    public MetricsOverviewResponse getOverview(
            @RequestParam(defaultValue = "24") @Min(1) @Max(MAX_WINDOW_HOURS) int hours) {
        return metricsService.getOverview(hours);
    }
}
