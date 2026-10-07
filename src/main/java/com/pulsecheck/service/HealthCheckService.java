package com.pulsecheck.service;

import com.pulsecheck.dto.HealthCheckResponse;
import com.pulsecheck.dto.PageResponse;
import com.pulsecheck.dto.ServiceHealthResponse;
import com.pulsecheck.entity.MonitoredService;
import com.pulsecheck.exception.ServiceNotFoundException;
import com.pulsecheck.repository.HealthCheckRepository;
import com.pulsecheck.repository.MonitoredServiceRepository;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HealthCheckService {

    private final HealthCheckRepository healthCheckRepository;
    private final MonitoredServiceRepository monitoredServiceRepository;

    public HealthCheckService(HealthCheckRepository healthCheckRepository,
                              MonitoredServiceRepository monitoredServiceRepository) {
        this.healthCheckRepository = healthCheckRepository;
        this.monitoredServiceRepository = monitoredServiceRepository;
    }

    public PageResponse<HealthCheckResponse> getHistory(Long serviceId, int page, int size) {
        ensureServiceExists(serviceId);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "checkedAt"));
        return PageResponse.from(
                healthCheckRepository.findByServiceId(serviceId, pageRequest)
                        .map(HealthCheckResponse::from));
    }

    public List<HealthCheckResponse> getRecent(Long serviceId, int limit) {
        ensureServiceExists(serviceId);
        return healthCheckRepository.findByServiceIdOrderByCheckedAtDesc(serviceId, Limit.of(limit)).stream()
                .map(HealthCheckResponse::from)
                .toList();
    }

    public ServiceHealthResponse getCurrentHealth(Long serviceId) {
        MonitoredService service = monitoredServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException(serviceId));

        return healthCheckRepository.findFirstByServiceIdOrderByCheckedAtDesc(serviceId)
                .map(latest -> new ServiceHealthResponse(
                        service.getId(),
                        service.getName(),
                        latest.getStatus().name(),
                        latest.getCheckedAt(),
                        latest.getResponseTimeMs(),
                        latest.getErrorMessage()))
                .orElseGet(() -> new ServiceHealthResponse(
                        service.getId(), service.getName(), "UNKNOWN", null, null, null));
    }

    private void ensureServiceExists(Long serviceId) {
        if (!monitoredServiceRepository.existsById(serviceId)) {
            throw new ServiceNotFoundException(serviceId);
        }
    }
}
