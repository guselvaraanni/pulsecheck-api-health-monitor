package com.pulsecheck.service;

import com.pulsecheck.dto.CheckRunItem;
import com.pulsecheck.dto.CheckRunResponse;
import com.pulsecheck.dto.CheckSummaryResponse;
import com.pulsecheck.dto.HealthCheckResponse;
import com.pulsecheck.dto.PageResponse;
import com.pulsecheck.dto.ServiceHealthResponse;
import com.pulsecheck.entity.HealthCheck;
import com.pulsecheck.entity.HealthStatus;
import com.pulsecheck.entity.MonitoredService;
import com.pulsecheck.exception.CheckRunInProgressException;
import com.pulsecheck.exception.ServiceNotFoundException;
import com.pulsecheck.repository.HealthCheckRepository;
import com.pulsecheck.repository.MonitoredServiceRepository;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class HealthCheckService {

    private static final int TOP_ERRORS_LIMIT = 5;

    private final HealthCheckRepository healthCheckRepository;
    private final MonitoredServiceRepository monitoredServiceRepository;
    private final HttpHealthChecker httpHealthChecker;
    private final ParallelHealthChecker parallelHealthChecker;
    private final CheckResultRecorder checkResultRecorder;

    // Shared by the scheduler thread and HTTP request threads, so it must be thread-safe.
    private final AtomicBoolean runInProgress = new AtomicBoolean(false);

    public HealthCheckService(HealthCheckRepository healthCheckRepository,
                              MonitoredServiceRepository monitoredServiceRepository,
                              HttpHealthChecker httpHealthChecker,
                              ParallelHealthChecker parallelHealthChecker,
                              CheckResultRecorder checkResultRecorder) {
        this.healthCheckRepository = healthCheckRepository;
        this.monitoredServiceRepository = monitoredServiceRepository;
        this.httpHealthChecker = httpHealthChecker;
        this.parallelHealthChecker = parallelHealthChecker;
        this.checkResultRecorder = checkResultRecorder;
    }

    /**
     * Checks all active services concurrently, then records every result.
     * HTTP calls run on the health-check thread pool; database work stays on the calling thread,
     * with one transaction per service so a failure for one service does not undo the others.
     * <p>
     * Only one run may be in progress at a time (scheduled or manual), so two runs can never check
     * the same service simultaneously and open duplicate incidents.
     *
     * @throws CheckRunInProgressException if another run has not finished yet
     */
    public CheckRunResponse runAllActiveChecks() {
        // compareAndSet is atomic: exactly one thread can switch false -> true, all others fail immediately.
        if (!runInProgress.compareAndSet(false, true)) {
            throw new CheckRunInProgressException();
        }
        try {
            return doRunAllActiveChecks();
        } finally {
            runInProgress.set(false);
        }
    }

    private CheckRunResponse doRunAllActiveChecks() {
        long start = System.nanoTime();

        List<MonitoredService> services = monitoredServiceRepository.findByActiveTrue();
        Map<MonitoredService, CheckResult> results = parallelHealthChecker.checkAll(services, MonitoredService::getUrl);

        List<CheckRunItem> items = results.entrySet().stream()
                .map(entry -> checkResultRecorder.record(entry.getKey(), entry.getValue()))
                .map(CheckRunItem::from)
                .toList();

        long upCount = items.stream().filter(item -> item.status() == HealthStatus.UP).count();
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        return new CheckRunResponse(items.size(), upCount, items.size() - upCount, durationMs, items);
    }

    /**
     * Checks a service now, regardless of its active flag, and records the result.
     * The HTTP call happens before the transaction starts, so a slow service never holds a DB connection.
     */
    public HealthCheckResponse runCheck(Long serviceId) {
        MonitoredService service = monitoredServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException(serviceId));

        CheckResult result = httpHealthChecker.check(service.getUrl());

        return HealthCheckResponse.from(checkResultRecorder.record(service, result));
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
