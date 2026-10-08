package com.pulsecheck.service;

import com.pulsecheck.entity.HealthCheck;
import com.pulsecheck.entity.MonitoredService;
import com.pulsecheck.repository.HealthCheckRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores a check result and updates incidents as one unit of work.
 * <p>
 * Kept as a separate bean from {@link HealthCheckService} on purpose: {@code @Transactional} works through
 * a Spring proxy, so it only applies when the method is called from another bean, not via {@code this}.
 */
@Service
public class CheckResultRecorder {

    private final HealthCheckRepository healthCheckRepository;
    private final IncidentService incidentService;

    public CheckResultRecorder(HealthCheckRepository healthCheckRepository, IncidentService incidentService) {
        this.healthCheckRepository = healthCheckRepository;
        this.incidentService = incidentService;
    }

    /**
     * Saves the check and applies incident transitions in one transaction:
     * either both are committed, or neither is.
     */
    @Transactional
    public HealthCheck record(MonitoredService service, CheckResult result) {
        HealthCheck check = healthCheckRepository.save(new HealthCheck(
                service,
                result.status(),
                result.responseTimeMs(),
                result.checkedAt(),
                result.errorMessage()));
        incidentService.applyCheck(check);
        return check;
    }
}
