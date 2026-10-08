package com.pulsecheck.service;

import com.pulsecheck.config.MonitoringProperties;
import com.pulsecheck.dto.CheckRunResponse;
import com.pulsecheck.exception.CheckRunInProgressException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs health checks for all active services in the background at a fixed interval.
 */
@Component
@ConditionalOnProperty(prefix = "pulsecheck.monitoring", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonitoringScheduler.class);

    private final HealthCheckService healthCheckService;
    private final MonitoringProperties properties;

    public MonitoringScheduler(HealthCheckService healthCheckService, MonitoringProperties properties) {
        this.healthCheckService = healthCheckService;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logSchedule() {
        log.info("Scheduled monitoring enabled: first run after {}, then every {} after each run finishes",
                properties.initialDelay(), properties.interval());
    }

    // fixedDelay waits for the previous run to finish before counting the interval, so runs never overlap.
    // fixedRate would start a run every interval, even if the previous one is still busy.
    @Scheduled(fixedDelayString = "${pulsecheck.monitoring.interval}",
            initialDelayString = "${pulsecheck.monitoring.initial-delay}")
    public void runScheduledChecks() {
        try {
            CheckRunResponse result = healthCheckService.runAllActiveChecks();
            log.info("Scheduled run: {} services checked, {} up, {} down in {} ms",
                    result.servicesChecked(), result.upCount(), result.downCount(), result.durationMs());
        } catch (CheckRunInProgressException e) {
            log.info("Scheduled run skipped: a manual run is already in progress");
        } catch (RuntimeException e) {
            // Catch everything so one bad run is logged clearly; the next run still happens on schedule.
            log.error("Scheduled run failed", e);
        }
    }
}
