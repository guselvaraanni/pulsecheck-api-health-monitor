package com.pulsecheck.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Performs a single HTTP GET against a URL and classifies the result as UP or DOWN.
 * Knows nothing about the database; callers decide what to do with the {@link CheckResult}.
 */
@Component
public class HttpHealthChecker {

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public HttpHealthChecker(HttpClient httpClient,
                             @Value("${pulsecheck.health-check.request-timeout}") Duration requestTimeout) {
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
    }

    public CheckResult check(String url) {
        Instant checkedAt = Instant.now();
        long start = System.nanoTime();

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(requestTimeout)
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            long elapsedMs = elapsedMillis(start);
            int statusCode = response.statusCode();

            if (statusCode >= 200 && statusCode < 400) {
                return CheckResult.up(elapsedMs, checkedAt);
            }
            return CheckResult.down(elapsedMs, checkedAt, "HTTP " + statusCode);

        } catch (HttpTimeoutException e) {
            return CheckResult.down(null, checkedAt, "Timed out: " + describe(e));
        } catch (ConnectException e) {
            return CheckResult.down(null, checkedAt, "Connection failed: " + describe(e));
        } catch (IOException e) {
            return CheckResult.down(null, checkedAt, "I/O error: " + describe(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CheckResult.down(null, checkedAt, "Check was interrupted");
        } catch (IllegalArgumentException e) {
            return CheckResult.down(null, checkedAt, "Invalid URL: " + describe(e));
        }
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    // Network exceptions often have a null message with the real reason in a nested cause,
    // e.g. ConnectException -> UnresolvedAddressException for an unknown host.
    private static String describe(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof UnresolvedAddressException) {
                return "unknown host";
            }
            if (current.getMessage() != null) {
                return current.getMessage();
            }
            if (current.getCause() == null) {
                return current.getClass().getSimpleName();
            }
            current = current.getCause();
        }
        return e.getClass().getSimpleName();
    }
}
