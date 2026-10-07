package com.pulsecheck.controller;

import com.pulsecheck.dto.ServiceRequest;
import com.pulsecheck.dto.ServiceResponse;
import com.pulsecheck.service.MonitoredServiceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/services")
public class MonitoredServiceController {

    private final MonitoredServiceService monitoredServiceService;

    public MonitoredServiceController(MonitoredServiceService monitoredServiceService) {
        this.monitoredServiceService = monitoredServiceService;
    }

    @PostMapping
    public ResponseEntity<ServiceResponse> create(@Valid @RequestBody ServiceRequest request) {
        ServiceResponse created = monitoredServiceService.create(request);
        return ResponseEntity
                .created(URI.create("/api/services/" + created.id()))
                .body(created);
    }

    @GetMapping
    public List<ServiceResponse> findAll() {
        return monitoredServiceService.findAll();
    }

    @GetMapping("/{id}")
    public ServiceResponse findById(@PathVariable Long id) {
        return monitoredServiceService.findById(id);
    }

    @PutMapping("/{id}")
    public ServiceResponse update(@PathVariable Long id, @Valid @RequestBody ServiceRequest request) {
        return monitoredServiceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        monitoredServiceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
