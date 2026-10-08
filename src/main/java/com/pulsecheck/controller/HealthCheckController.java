package com.pulsecheck.controller;

import com.pulsecheck.dto.CheckSummaryResponse;
import com.pulsecheck.dto.HealthCheckResponse;
import com.pulsecheck.dto.PageResponse;
import com.pulsecheck.dto.ServiceHealthResponse;
import com.pulsecheck.service.HealthCheckService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/services/{serviceId}")
public class HealthCheckController {

    private final HealthCheckService healthCheckService;

    public HealthCheckController(HealthCheckService healthCheckService) {
        this.healthCheckService = healthCheckService;
    }

    // A DOWN result is still a successful API call: the check ran and was recorded.
    @PostMapping("/checks")
    @ResponseStatus(HttpStatus.CREATED)
    public HealthCheckResponse runCheck(@PathVariable Long serviceId) {
        return healthCheckService.runCheck(serviceId);
    }

    @GetMapping("/checks")
    public PageResponse<HealthCheckResponse> getHistory(
            @PathVariable Long serviceId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return healthCheckService.getHistory(serviceId, page, size);
    }

    @GetMapping("/checks/summary")
    public CheckSummaryResponse getSummary(
            @PathVariable Long serviceId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        return healthCheckService.getSummary(serviceId, limit);
    }

    @GetMapping("/health")
    public ServiceHealthResponse getCurrentHealth(@PathVariable Long serviceId) {
        return healthCheckService.getCurrentHealth(serviceId);
    }
}
