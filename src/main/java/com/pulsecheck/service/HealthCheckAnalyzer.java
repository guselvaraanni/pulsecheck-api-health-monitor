package com.pulsecheck.service;

import com.pulsecheck.dto.ErrorCount;
import com.pulsecheck.entity.HealthCheck;
import com.pulsecheck.entity.HealthStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * Pure calculations over a list of health checks. No database or Spring dependencies,
 * so every method can be unit-tested with plain objects.
 */
public final class HealthCheckAnalyzer {

    private static final Predicate<HealthCheck> IS_UP = check -> check.getStatus() == HealthStatus.UP;
    private static final Predicate<HealthCheck> IS_DOWN = IS_UP.negate();

    private HealthCheckAnalyzer() {
    }

    /** Number of checks per status. Every status is present, with 0 if it never occurred. */
    public static Map<HealthStatus, Long> countByStatus(List<HealthCheck> checks) {
        Map<HealthStatus, Long> counts = checks.stream()
                .collect(Collectors.groupingBy(
                        HealthCheck::getStatus,
                        () -> new EnumMap<>(HealthStatus.class),
                        Collectors.counting()));
        for (HealthStatus status : HealthStatus.values()) {
            counts.putIfAbsent(status, 0L);
        }
        return counts;
    }

    public static long countFailures(List<HealthCheck> checks) {
        return checks.stream()
                .filter(IS_DOWN)
                .count();
    }

    /**
     * Number of DOWN checks in a row, starting from the newest.
     * Expects {@code checksNewestFirst} to be sorted by checkedAt descending.
     */
    public static long consecutiveFailures(List<HealthCheck> checksNewestFirst) {
        return checksNewestFirst.stream()
                .takeWhile(IS_DOWN)
                .count();
    }

    public static OptionalLong fastestResponseMs(List<HealthCheck> checks) {
        return successfulResponseTimes(checks).min();
    }

    public static OptionalLong slowestResponseMs(List<HealthCheck> checks) {
        return successfulResponseTimes(checks).max();
    }

    public static Optional<Instant> lastFailureAt(List<HealthCheck> checks) {
        return checks.stream()
                .filter(IS_DOWN)
                .map(HealthCheck::getCheckedAt)
                .max(Comparator.naturalOrder());
    }

    /** Most frequent failure reasons, most common first. */
    public static List<ErrorCount> topErrors(List<HealthCheck> checks, int limit) {
        Map<String, Long> countsByMessage = checks.stream()
                .filter(IS_DOWN)
                // groupingBy throws NullPointerException on a null key, so give missing messages a label.
                .map(check -> Objects.requireNonNullElse(check.getErrorMessage(), "Unknown error"))
                .collect(Collectors.groupingBy(message -> message, Collectors.counting()));

        return countsByMessage.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(entry -> new ErrorCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    // Only UP checks have a meaningful response time; failed checks may have none.
    private static LongStream successfulResponseTimes(List<HealthCheck> checks) {
        return checks.stream()
                .filter(IS_UP)
                .map(HealthCheck::getResponseTimeMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue);
    }
}
