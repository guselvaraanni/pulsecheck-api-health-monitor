package com.pulsecheck.dto;

/**
 * Request body for creating or updating a monitored service.
 * {@code active} is optional: it defaults to true on create and stays unchanged on update when omitted.
 */
public record ServiceRequest(String name, String url, String description, Boolean active) {
}
