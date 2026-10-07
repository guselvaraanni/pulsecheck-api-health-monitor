package com.pulsecheck.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class HttpClientConfig {

    // HttpClient is thread-safe and keeps a connection pool, so one shared instance is reused for all checks.
    @Bean
    public HttpClient httpClient(@Value("${pulsecheck.health-check.connect-timeout}") Duration connectTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
