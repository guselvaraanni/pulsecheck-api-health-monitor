package com.pulsecheck.controller;

import com.pulsecheck.dto.IncidentResponse;
import com.pulsecheck.dto.PageResponse;
import com.pulsecheck.service.IncidentService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping("/api/incidents")
    public PageResponse<IncidentResponse> getIncidents(
            @RequestParam(defaultValue = "false") boolean open,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return incidentService.getIncidents(open, page, size);
    }

    @GetMapping("/api/services/{serviceId}/incidents")
    public PageResponse<IncidentResponse> getIncidentsForService(
            @PathVariable Long serviceId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return incidentService.getIncidentsForService(serviceId, page, size);
    }
}
