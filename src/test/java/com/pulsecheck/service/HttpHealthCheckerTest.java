package com.pulsecheck.service;

import com.pulsecheck.entity.HealthStatus;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Plain unit test: a JDK HttpServer on a random local port plays the role of the monitored service.
class HttpHealthCheckerTest {

    private static HttpServer server;
    private static ExecutorService serverExecutor;
    private static String baseUrl;

    private final HttpHealthChecker checker = new HttpHealthChecker(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
            Duration.ofMillis(500));

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/ok", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/error", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        serverExecutor = Executors.newFixedThreadPool(4);
        server.setExecutor(serverExecutor);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    @Test
    void successfulResponseIsUp() {
        CheckResult result = checker.check(baseUrl + "/ok");

        assertEquals(HealthStatus.UP, result.status());
        assertNotNull(result.responseTimeMs());
        assertNull(result.errorMessage());
    }

    @Test
    void serverErrorIsDownWithStatusCode() {
        CheckResult result = checker.check(baseUrl + "/error");

        assertEquals(HealthStatus.DOWN, result.status());
        assertEquals("HTTP 503", result.errorMessage());
        assertNotNull(result.responseTimeMs());
    }

    @Test
    void slowResponseTimesOut() {
        CheckResult result = checker.check(baseUrl + "/slow");

        assertEquals(HealthStatus.DOWN, result.status());
        assertTrue(result.errorMessage().startsWith("Timed out"), result.errorMessage());
        assertNull(result.responseTimeMs());
    }

    @Test
    void closedPortIsConnectionFailure() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }

        CheckResult result = checker.check("http://localhost:" + freePort + "/health");

        assertEquals(HealthStatus.DOWN, result.status());
        assertTrue(result.errorMessage().startsWith("Connection failed"), result.errorMessage());
    }

    @Test
    void unknownHostIsDown() {
        CheckResult result = checker.check("http://pulsecheck-does-not-exist.invalid/health");

        assertEquals(HealthStatus.DOWN, result.status());
        assertEquals("Connection failed: unknown host", result.errorMessage());
    }
}
