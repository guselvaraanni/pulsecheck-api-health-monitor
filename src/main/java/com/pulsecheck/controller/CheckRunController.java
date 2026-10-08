package com.pulsecheck.controller;

import com.pulsecheck.dto.CheckRunResponse;
import com.pulsecheck.service.HealthCheckService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checks")
public class CheckRunController {

    private final HealthCheckService healthCheckService;

    public CheckRunController(HealthCheckService healthCheckService) {
        this.healthCheckService = healthCheckService;
    }

    @PostMapping("/run")
    public CheckRunResponse runAllActiveChecks() {
        return healthCheckService.runAllActiveChecks();
    }
}
