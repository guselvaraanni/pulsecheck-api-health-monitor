package com.pulsecheck.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Runs HTTP checks for many targets at the same time on the shared health-check thread pool.
 * Worker threads only perform network calls; they never touch the database or shared mutable state.
 */
@Component
public class ParallelHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(ParallelHealthChecker.class);

    private final ExecutorService healthCheckExecutor;
    private final HttpHealthChecker httpHealthChecker;

    public ParallelHealthChecker(ExecutorService healthCheckExecutor, HttpHealthChecker httpHealthChecker) {
        this.healthCheckExecutor = healthCheckExecutor;
        this.httpHealthChecker = httpHealthChecker;
    }

    /**
     * Checks every target concurrently and waits for all results.
     *
     * @param targets      the things to check (e.g. services); returned map keeps the same order
     * @param urlExtractor how to get the URL from a target; called on the caller's thread
     */
    public <T> Map<T, CheckResult> checkAll(List<T> targets, Function<T, String> urlExtractor) {
        // 1. Submit every task first, so they all run in parallel.
        Map<T, Future<CheckResult>> futures = new LinkedHashMap<>();
        for (T target : targets) {
            String url = urlExtractor.apply(target);
            futures.put(target, healthCheckExecutor.submit(() -> checkAndLog(url)));
        }

        // 2. Then wait for each one. Total time is roughly the slowest check, not the sum of all checks.
        Map<T, CheckResult> results = new LinkedHashMap<>();
        for (Map.Entry<T, Future<CheckResult>> entry : futures.entrySet()) {
            results.put(entry.getKey(), await(entry.getValue()));
        }
        return results;
    }

    private CheckResult checkAndLog(String url) {
        CheckResult result = httpHealthChecker.check(url);
        log.info("Checked {} -> {} ({} ms)", url, result.status(), result.responseTimeMs());
        return result;
    }

    private CheckResult await(Future<CheckResult> future) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            // HttpHealthChecker catches network errors itself, so this means an unexpected bug in the task.
            log.error("Health check task failed unexpectedly", e.getCause());
            return CheckResult.down(null, Instant.now(), "Check failed unexpectedly: " + e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return CheckResult.down(null, Instant.now(), "Check was interrupted");
        }
    }
}
