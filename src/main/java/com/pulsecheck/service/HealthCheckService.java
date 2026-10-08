package com.pulsecheck.service;

import com.pulsecheck.dto.CheckSummaryResponse;
import com.pulsecheck.dto.HealthCheckResponse;
import com.pulsecheck.dto.PageResponse;
import com.pulsecheck.dto.ServiceHealthResponse;
import com.pulsecheck.entity.HealthCheck;
import com.pulsecheck.entity.MonitoredService;
import com.pulsecheck.exception.ServiceNotFoundException;
import com.pulsecheck.repository.HealthCheckRepository;
import com.pulsecheck.repository.MonitoredServiceRepository;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.OptionalLong;

@Service
public class HealthCheckService {

    private static final int TOP_ERRORS_LIMIT = 5;

    private final HealthCheckRepository healthCheckRepository;
    private final MonitoredServiceRepository monitoredServiceRepository;
    private final HttpHealthChecker httpHealthChecker;

    public HealthCheckService(HealthCheckRepository healthCheckRepository,
                              MonitoredServiceRepository monitoredServiceRepository,
                              HttpHealthChecker httpHealthChecker) {
        this.healthCheckRepository = healthCheckRepository;
        this.monitoredServiceRepository = monitoredServiceRepository;
        this.httpHealthChecker = httpHealthChecker;
    }

    /**
     * Checks a service now, regardless of its active flag, and stores the result.
     * The HTTP call happens before any database write, so a slow service never holds a DB connection.
     */
    public HealthCheckResponse runCheck(Long serviceId) {
        MonitoredService service = monitoredServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException(serviceId));

        CheckResult result = httpHealthChecker.check(service.getUrl());

        HealthCheck saved = healthCheckRepository.save(new HealthCheck(
                service,
                result.status(),
                result.responseTimeMs(),
                result.checkedAt(),
                result.errorMessage()));
        return HealthCheckResponse.from(saved);
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

    public CheckSummaryResponse getSummary(Long serviceId, int limit) {
        MonitoredService service = monitoredServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException(serviceId));

        List<HealthCheck> checks =
                healthCheckRepository.findByServiceIdOrderByCheckedAtDesc(serviceId, Limit.of(limit));

        return new CheckSummaryResponse(
                service.getId(),
                service.getName(),
                checks.size(),
                HealthCheckAnalyzer.countByStatus(checks),
                HealthCheckAnalyzer.countFailures(checks),
                HealthCheckAnalyzer.consecutiveFailures(checks),
                toNullable(HealthCheckAnalyzer.fastestResponseMs(checks)),
                toNullable(HealthCheckAnalyzer.slowestResponseMs(checks)),
                HealthCheckAnalyzer.lastFailureAt(checks).orElse(null),
                HealthCheckAnalyzer.topErrors(checks, TOP_ERRORS_LIMIT));
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

    private static Long toNullable(OptionalLong value) {
        return value.isPresent() ? value.getAsLong() : null;
    }
}
