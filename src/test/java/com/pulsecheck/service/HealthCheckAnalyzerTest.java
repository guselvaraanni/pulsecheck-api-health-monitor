package com.pulsecheck.service;

import com.pulsecheck.dto.ErrorCount;
import com.pulsecheck.entity.HealthCheck;
import com.pulsecheck.entity.HealthStatus;
import com.pulsecheck.entity.MonitoredService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthCheckAnalyzerTest {

    private static final MonitoredService SERVICE = new MonitoredService("Test", "https://example.com", null);
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private static HealthCheck up(int minutesAgo, long responseMs) {
        return new HealthCheck(SERVICE, HealthStatus.UP, responseMs, NOW.minusSeconds(minutesAgo * 60L), null);
    }

    private static HealthCheck down(int minutesAgo, String error) {
        return new HealthCheck(SERVICE, HealthStatus.DOWN, null, NOW.minusSeconds(minutesAgo * 60L), error);
    }

    // Newest first, as returned by the repository.
    private final List<HealthCheck> checks = List.of(
            down(0, "HTTP 500"),
            down(1, "Timed out"),
            down(2, "HTTP 500"),
            up(3, 200),
            down(4, "HTTP 500"),
            up(5, 80),
            up(6, 150));

    @Test
    void countsChecksPerStatus() {
        Map<HealthStatus, Long> counts = HealthCheckAnalyzer.countByStatus(checks);

        assertEquals(3L, counts.get(HealthStatus.UP));
        assertEquals(4L, counts.get(HealthStatus.DOWN));
    }

    @Test
    void countByStatusIncludesStatusesThatNeverOccurred() {
        Map<HealthStatus, Long> counts = HealthCheckAnalyzer.countByStatus(List.of(up(0, 100)));

        assertEquals(0L, counts.get(HealthStatus.DOWN));
    }

    @Test
    void countsFailures() {
        assertEquals(4, HealthCheckAnalyzer.countFailures(checks));
    }

    @Test
    void consecutiveFailuresStopAtFirstSuccess() {
        assertEquals(3, HealthCheckAnalyzer.consecutiveFailures(checks));
        assertEquals(0, HealthCheckAnalyzer.consecutiveFailures(List.of(up(0, 100), down(1, "x"))));
    }

    @Test
    void fastestAndSlowestUseOnlySuccessfulChecks() {
        assertEquals(OptionalLong.of(80), HealthCheckAnalyzer.fastestResponseMs(checks));
        assertEquals(OptionalLong.of(200), HealthCheckAnalyzer.slowestResponseMs(checks));
    }

    @Test
    void lastFailureIsTheNewestDownCheck() {
        assertEquals(Optional.of(NOW), HealthCheckAnalyzer.lastFailureAt(checks));
    }

    @Test
    void topErrorsAreSortedByFrequency() {
        List<ErrorCount> top = HealthCheckAnalyzer.topErrors(checks, 5);

        assertEquals(List.of(new ErrorCount("HTTP 500", 3), new ErrorCount("Timed out", 1)), top);
    }

    @Test
    void topErrorsHandlesMissingMessagesAndLimit() {
        List<ErrorCount> top = HealthCheckAnalyzer.topErrors(
                List.of(down(0, null), down(1, null), down(2, "HTTP 503")), 1);

        assertEquals(List.of(new ErrorCount("Unknown error", 2)), top);
    }

    @Test
    void emptyListProducesEmptyResults() {
        List<HealthCheck> none = List.of();

        assertEquals(0, HealthCheckAnalyzer.countFailures(none));
        assertEquals(0, HealthCheckAnalyzer.consecutiveFailures(none));
        assertTrue(HealthCheckAnalyzer.fastestResponseMs(none).isEmpty());
        assertTrue(HealthCheckAnalyzer.lastFailureAt(none).isEmpty());
        assertTrue(HealthCheckAnalyzer.topErrors(none, 5).isEmpty());
        assertEquals(0L, HealthCheckAnalyzer.countByStatus(none).get(HealthStatus.UP));
    }
}
