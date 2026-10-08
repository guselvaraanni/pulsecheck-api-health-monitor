package com.pulsecheck.service;

import com.pulsecheck.exception.CheckRunInProgressException;
import com.pulsecheck.repository.HealthCheckRepository;
import com.pulsecheck.repository.MonitoredServiceRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Two threads try to run all checks at once: the second must be rejected while the first is busy.
 */
class RunAllChecksGuardTest {

    private final MonitoredServiceRepository serviceRepository = mock(MonitoredServiceRepository.class);
    private final HealthCheckService service = new HealthCheckService(
            mock(HealthCheckRepository.class),
            serviceRepository,
            mock(HttpHealthChecker.class),
            mock(ParallelHealthChecker.class),
            mock(CheckResultRecorder.class));

    @Test
    void secondRunIsRejectedWhileFirstIsInProgress() throws Exception {
        CountDownLatch firstRunStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstRun = new CountDownLatch(1);

        // The first run blocks inside the guarded section until the test releases it.
        when(serviceRepository.findByActiveTrue()).thenAnswer(invocation -> {
            firstRunStarted.countDown();
            releaseFirstRun.await(5, TimeUnit.SECONDS);
            return List.of();
        });

        CompletableFuture<Void> firstRun = CompletableFuture.runAsync(service::runAllActiveChecks);
        assertTrue(firstRunStarted.await(5, TimeUnit.SECONDS), "first run should have started");

        assertThrows(CheckRunInProgressException.class, service::runAllActiveChecks);

        releaseFirstRun.countDown();
        firstRun.get(5, TimeUnit.SECONDS);

        // Once the first run has finished, a new run is allowed again.
        assertEquals(0, service.runAllActiveChecks().servicesChecked());
    }

    @Test
    void guardIsReleasedEvenWhenARunFails() {
        when(serviceRepository.findByActiveTrue())
                .thenThrow(new IllegalStateException("database down"))
                .thenReturn(List.of());

        assertThrows(IllegalStateException.class, service::runAllActiveChecks);
        assertEquals(0, service.runAllActiveChecks().servicesChecked());
    }
}
