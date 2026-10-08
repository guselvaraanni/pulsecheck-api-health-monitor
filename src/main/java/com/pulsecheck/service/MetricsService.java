package com.pulsecheck.service;

import com.pulsecheck.dto.MetricsOverviewResponse;
import com.pulsecheck.dto.ServiceMetricsResponse;
import com.pulsecheck.entity.HealthCheck;
import com.pulsecheck.entity.MonitoredService;
import com.pulsecheck.exception.ServiceNotFoundException;
import com.pulsecheck.repository.HealthCheckRepository;
import com.pulsecheck.repository.MonitoredServiceRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Availability metrics calculated from stored health checks within a time window.
 * Windows are capped by the controller, so the number of checks loaded into memory stays bounded.
 */
@Service
public class MetricsService {

    private final HealthCheckRepository healthCheckRepository;
    private final MonitoredServiceRepository monitoredServiceRepository;

    public MetricsService(HealthCheckRepository healthCheckRepository,
                          MonitoredServiceRepository monitoredServiceRepository) {
        this.healthCheckRepository = healthCheckRepository;
        this.monitoredServiceRepository = monitoredServiceRepository;
    }

    public ServiceMetricsResponse getServiceMetrics(Long serviceId, int hours) {
        MonitoredService service = monitoredServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException(serviceId));

        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofHours(hours));
        List<HealthCheck> checks = healthCheckRepository.findByServiceIdAndCheckedAtGreaterThanEqual(serviceId, from);

        return ServiceMetricsResponse.of(service, from, to, AvailabilityStats.from(checks));
    }

    public MetricsOverviewResponse getOverview(int hours) {
        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofHours(hours));

        // One query for all checks in the window, grouped by service in memory.
        // check.getService().getId() does not load the service: Hibernate's lazy proxy already knows its id.
        Map<Long, List<HealthCheck>> checksByServiceId = healthCheckRepository.findByCheckedAtGreaterThanEqual(from)
                .stream()
                .collect(Collectors.groupingBy(check -> check.getService().getId()));

        // Start from all services, so services without any checks still appear (with null availability).
        List<ServiceMetricsResponse> services = monitoredServiceRepository.findAll().stream()
                .map(service -> ServiceMetricsResponse.of(service, from, to,
                        AvailabilityStats.from(checksByServiceId.getOrDefault(service.getId(), List.of()))))
                .sorted(Comparator.comparing(ServiceMetricsResponse::availabilityPercent,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ServiceMetricsResponse::serviceName))
                .toList();

        long totalChecks = services.stream().mapToLong(ServiceMetricsResponse::totalChecks).sum();
        long successfulChecks = services.stream().mapToLong(ServiceMetricsResponse::successfulChecks).sum();

        return new MetricsOverviewResponse(
                from,
                to,
                services.size(),
                totalChecks,
                AvailabilityStats.percentage(successfulChecks, totalChecks),
                services);
    }
}
