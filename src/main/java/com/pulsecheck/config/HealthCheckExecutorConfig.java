package com.pulsecheck.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class HealthCheckExecutorConfig {

    // Fixed size: at most poolSize checks run at once, no matter how many services are registered.
    // Spring calls shutdown() when the application stops, so worker threads do not outlive the app.
    @Bean(destroyMethod = "shutdown")
    public ExecutorService healthCheckExecutor(@Value("${pulsecheck.health-check.thread-pool-size}") int poolSize) {
        return Executors.newFixedThreadPool(poolSize, Thread.ofPlatform().name("health-check-", 1).factory());
    }
}
