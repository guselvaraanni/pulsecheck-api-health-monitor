package com.pulsecheck.service;

import com.pulsecheck.config.MonitoringProperties;
import com.pulsecheck.dto.CheckRunResponse;
import com.pulsecheck.exception.CheckRunInProgressException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonitoringSchedulerTest {

    private final HealthCheckService healthCheckService = mock(HealthCheckService.class);
    private final MonitoringScheduler scheduler = new MonitoringScheduler(
            healthCheckService, new MonitoringProperties(true, Duration.ofSeconds(30), Duration.ZERO));

    @Test
    void runsAllActiveChecks() {
        when(healthCheckService.runAllActiveChecks()).thenReturn(new CheckRunResponse(0, 0, 0, 1, List.of()));

        scheduler.runScheduledChecks();

        verify(healthCheckService, times(1)).runAllActiveChecks();
    }

    @Test
    void failuresDoNotEscapeTheScheduledMethod() {
        when(healthCheckService.runAllActiveChecks())
                .thenThrow(new IllegalStateException("database down"))
                .thenThrow(new CheckRunInProgressException());

        assertDoesNotThrow(scheduler::runScheduledChecks);
        assertDoesNotThrow(scheduler::runScheduledChecks);
    }
}
