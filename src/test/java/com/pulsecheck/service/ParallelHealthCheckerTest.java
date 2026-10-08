package com.pulsecheck.service;

import com.pulsecheck.entity.HealthStatus;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParallelHealthCheckerTest {

    private static final int SLOW_RESPONSE_MS = 500;
    private static final int TARGET_COUNT = 8;

    private static HttpServer server;
    private static ExecutorService serverExecutor;
    private static String baseUrl;

    // Updated by many server threads at once, so they must be atomic.
    private static final AtomicInteger inFlight = new AtomicInteger();
    private static final AtomicInteger maxInFlight = new AtomicInteger();

    private final HttpHealthChecker httpHealthChecker = new HttpHealthChecker(
            HttpClient.newHttpClient(), Duration.ofSeconds(5));
    private ExecutorService pool;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/slow", exchange -> {
            int now = inFlight.incrementAndGet();
            maxInFlight.updateAndGet(max -> Math.max(max, now));
            try {
                Thread.sleep(SLOW_RESPONSE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        // The fake server must be able to handle many requests at once, or it would serialize them itself.
        serverExecutor = Executors.newFixedThreadPool(TARGET_COUNT * 2);
        server.setExecutor(serverExecutor);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    @BeforeEach
    void resetCounters() {
        inFlight.set(0);
        maxInFlight.set(0);
    }

    @AfterEach
    void shutdownPool() {
        pool.shutdownNow();
    }

    private ParallelHealthChecker checkerWithPoolSize(int size) {
        pool = Executors.newFixedThreadPool(size);
        return new ParallelHealthChecker(pool, httpHealthChecker);
    }

    private static List<String> targets() {
        List<String> names = new ArrayList<>();
        for (int i = 1; i <= TARGET_COUNT; i++) {
            names.add("service-" + i);
        }
        return names;
    }

    @Test
    void checksRunConcurrentlyAndRespectPoolSize() {
        ParallelHealthChecker checker = checkerWithPoolSize(4);

        long start = System.nanoTime();
        Map<String, CheckResult> results = checker.checkAll(targets(), name -> baseUrl + "/slow?name=" + name);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertEquals(TARGET_COUNT, results.size());
        assertTrue(results.values().stream().allMatch(r -> r.status() == HealthStatus.UP));
        // 8 checks x 500 ms on 4 threads = about 1 s. One-by-one would take about 4 s.
        assertTrue(elapsedMs < 2_500, "expected concurrent execution, took " + elapsedMs + " ms");
        assertEquals(4, maxInFlight.get(), "pool size should cap concurrent requests");
    }

    @Test
    void resultsKeepTheOrderOfTheInput() {
        ParallelHealthChecker checker = checkerWithPoolSize(4);

        Map<String, CheckResult> results = checker.checkAll(targets(), name -> baseUrl + "/slow?name=" + name);

        assertEquals(targets(), List.copyOf(results.keySet()));
    }

    @Test
    void singleThreadPoolRunsChecksOneByOne() {
        ParallelHealthChecker checker = checkerWithPoolSize(1);

        long start = System.nanoTime();
        checker.checkAll(targets().subList(0, 4), name -> baseUrl + "/slow?name=" + name);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertTrue(elapsedMs >= 4L * SLOW_RESPONSE_MS, "expected sequential execution, took " + elapsedMs + " ms");
        assertEquals(1, maxInFlight.get());
    }
}
