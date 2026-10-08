package com.pulsecheck.service;

import com.pulsecheck.entity.HealthStatus;
import com.pulsecheck.entity.MonitoredService;
import com.pulsecheck.repository.HealthCheckRepository;
import com.pulsecheck.repository.MonitoredServiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Proves the transaction boundary of {@link CheckResultRecorder#record}: if updating incidents fails,
 * the health check saved earlier in the same method is rolled back too.
 * Deliberately NOT @Transactional, so commits and rollbacks really happen.
 */
@SpringBootTest(properties = "pulsecheck.monitoring.enabled=false")
class CheckResultRecorderRollbackTest {

    @Autowired
    private CheckResultRecorder recorder;

    @Autowired
    private MonitoredServiceRepository serviceRepository;

    @Autowired
    private HealthCheckRepository healthCheckRepository;

    @MockitoBean
    private IncidentService incidentService;

    private MonitoredService service;

    @AfterEach
    void cleanUp() {
        if (service != null) {
            serviceRepository.delete(service);
        }
    }

    @Test
    void healthCheckIsRolledBackWhenIncidentUpdateFails() {
        service = serviceRepository.save(new MonitoredService("Rollback Test API", "https://example.com", null));
        doThrow(new IllegalStateException("simulated failure")).when(incidentService).applyCheck(any());

        assertThrows(IllegalStateException.class, () -> recorder.record(service,
                new CheckResult(HealthStatus.DOWN, null, Instant.now(), "HTTP 500")));

        assertTrue(healthCheckRepository
                        .findByServiceIdOrderByCheckedAtDesc(service.getId(), Limit.unlimited())
                        .isEmpty(),
                "health check insert should have been rolled back");
    }
}
