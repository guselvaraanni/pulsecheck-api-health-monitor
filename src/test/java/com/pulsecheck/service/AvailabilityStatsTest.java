package com.pulsecheck.service;

import com.pulsecheck.entity.HealthCheck;
import com.pulsecheck.entity.HealthStatus;
import com.pulsecheck.entity.MonitoredService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AvailabilityStatsTest {

    private static final MonitoredService SERVICE = new MonitoredService("Test", "https://example.com", null);

    private static HealthCheck up(long responseMs) {
        return new HealthCheck(SERVICE, HealthStatus.UP, responseMs, Instant.now(), null);
    }

    private static HealthCheck down() {
        return new HealthCheck(SERVICE, HealthStatus.DOWN, null, Instant.now(), "HTTP 500");
    }

    @Test
    void calculatesAvailabilityAndAverage() {
        List<HealthCheck> checks = new ArrayList<>();
        for (int i = 0; i < 96; i++) {
            checks.add(up(i % 2 == 0 ? 150 : 210));
        }
        for (int i = 0; i < 4; i++) {
            checks.add(down());
        }

        AvailabilityStats stats = AvailabilityStats.from(checks);

        assertEquals(100, stats.totalChecks());
        assertEquals(96, stats.successfulChecks());
        assertEquals(4, stats.failedChecks());
        assertEquals(96.0, stats.availabilityPercent());
        assertEquals(180L, stats.averageResponseTimeMs());
    }

    @Test
    void emptyWindowHasNoAvailabilityInsteadOfZero() {
        AvailabilityStats stats = AvailabilityStats.from(List.of());

        assertEquals(0, stats.totalChecks());
        assertNull(stats.availabilityPercent());
        assertNull(stats.averageResponseTimeMs());
    }

    @Test
    void allFailedMeansZeroAvailabilityAndNoAverage() {
        AvailabilityStats stats = AvailabilityStats.from(List.of(down(), down()));

        assertEquals(0.0, stats.availabilityPercent());
        assertNull(stats.averageResponseTimeMs());
    }

    @Test
    void percentageIsRoundedToTwoDecimals() {
        assertEquals(66.67, AvailabilityStats.percentage(2, 3));
        assertEquals(99.9, AvailabilityStats.percentage(999, 1000));
        assertEquals(100.0, AvailabilityStats.percentage(5, 5));
    }

    @Test
    void percentageAvoidsIntegerDivisionAndDivisionByZero() {
        assertEquals(1.0, AvailabilityStats.percentage(1, 100));
        assertNull(AvailabilityStats.percentage(0, 0));
    }

    @Test
    void averageIsRoundedToNearestMillisecond() {
        AvailabilityStats stats = AvailabilityStats.from(List.of(up(100), up(101)));

        assertEquals(101L, stats.averageResponseTimeMs());
    }
}
