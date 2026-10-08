package com.pulsecheck.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Scheduling is switched on only when pulsecheck.monitoring.enabled=true (the default).
// Tests set it to false so background runs do not interfere with them.
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "pulsecheck.monitoring", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
