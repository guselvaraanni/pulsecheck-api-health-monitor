package com.pulsecheck.dto;

import java.time.Instant;

public record PingResponse(String status, String application, Instant timestamp) {
}
