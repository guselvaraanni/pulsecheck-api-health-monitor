package com.pulsecheck.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * Request body for creating or updating a monitored service.
 * {@code active} is optional: it defaults to true on create and stays unchanged on update when omitted.
 */
public record ServiceRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotBlank(message = "URL is required")
        @Size(max = 2048, message = "URL must be at most 2048 characters")
        @URL(message = "URL must be a valid URL")
        @Pattern(regexp = "^https?://.*", message = "URL must start with http:// or https://")
        String url,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description,

        Boolean active) {
}
