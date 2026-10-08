package com.pulsecheck.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for automatic monitoring, bound from {@code pulsecheck.monitoring.*}.
 * Invalid values stop the application at startup instead of failing later in the background.
 */
@ConfigurationProperties(prefix = "pulsecheck.monitoring")
public record MonitoringProperties(boolean enabled, Duration interval, Duration initialDelay) {

    private static final Duration MIN_INTERVAL = Duration.ofSeconds(1);

    public MonitoringProperties {
        if (interval == null || interval.compareTo(MIN_INTERVAL) < 0) {
            throw new IllegalArgumentException(
                    "pulsecheck.monitoring.interval must be at least " + MIN_INTERVAL.toSeconds() + "s, was " + interval);
        }
        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "pulsecheck.monitoring.initial-delay must be zero or positive, was " + initialDelay);
        }
    }
}
