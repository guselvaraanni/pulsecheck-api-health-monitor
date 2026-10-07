package com.pulsecheck.controller;

import com.pulsecheck.dto.PingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api")
public class PingController {

    private final String applicationName;

    public PingController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping("/ping")
    public PingResponse ping() {
        return new PingResponse("UP", applicationName, Instant.now());
    }
}
