package com.pulsecheck.dto;

import java.util.List;

/**
 * Result of checking all active services in one run.
 */
public record CheckRunResponse(
        int servicesChecked,
        long upCount,
        long downCount,
        long durationMs,
        List<CheckRunItem> results) {
}
