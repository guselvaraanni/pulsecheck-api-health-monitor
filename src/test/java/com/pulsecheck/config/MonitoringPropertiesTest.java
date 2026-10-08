package com.pulsecheck.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MonitoringPropertiesTest {

    @Test
    void acceptsValidSettings() {
        assertDoesNotThrow(() -> new MonitoringProperties(true, Duration.ofSeconds(30), Duration.ZERO));
    }

    @Test
    void rejectsTooShortOrMissingInterval() {
        assertThrows(IllegalArgumentException.class,
                () -> new MonitoringProperties(true, Duration.ofMillis(500), Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new MonitoringProperties(true, null, Duration.ZERO));
    }

    @Test
    void rejectsNegativeInitialDelay() {
        assertThrows(IllegalArgumentException.class,
                () -> new MonitoringProperties(true, Duration.ofSeconds(30), Duration.ofSeconds(-1)));
    }
}
