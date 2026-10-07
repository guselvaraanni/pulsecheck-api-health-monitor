package com.pulsecheck.repository;

import com.pulsecheck.entity.HealthCheck;
import com.pulsecheck.entity.HealthStatus;
import com.pulsecheck.entity.MonitoredService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class HealthCheckRepositoryTest {

    @Autowired
    private HealthCheckRepository healthCheckRepository;

    @Autowired
    private MonitoredServiceRepository serviceRepository;

    @Autowired
    private EntityManager entityManager;

    private MonitoredService service;
    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        service = serviceRepository.save(new MonitoredService("Test API", "https://example.com", null));
        healthCheckRepository.save(new HealthCheck(service, HealthStatus.UP, 120L, now.minusSeconds(60), null));
        healthCheckRepository.save(new HealthCheck(service, HealthStatus.DOWN, null, now, "Connection refused"));
        healthCheckRepository.save(new HealthCheck(service, HealthStatus.UP, 95L, now.minusSeconds(120), null));
        entityManager.flush();
    }

    @Test
    void recentChecksAreNewestFirstAndLimited() {
        List<HealthCheck> recent = healthCheckRepository.findByServiceIdOrderByCheckedAtDesc(service.getId(), Limit.of(2));

        assertEquals(2, recent.size());
        assertEquals(now, recent.get(0).getCheckedAt());
        assertEquals(now.minusSeconds(60), recent.get(1).getCheckedAt());
    }

    @Test
    void latestCheckIsTheMostRecentOne() {
        HealthCheck latest = healthCheckRepository.findFirstByServiceIdOrderByCheckedAtDesc(service.getId()).orElseThrow();

        assertEquals(HealthStatus.DOWN, latest.getStatus());
        assertEquals("Connection refused", latest.getErrorMessage());
    }

    @Test
    void deletingServiceDeletesItsChecksInTheDatabase() {
        serviceRepository.delete(service);
        entityManager.flush();
        entityManager.clear();

        assertTrue(healthCheckRepository
                .findByServiceIdOrderByCheckedAtDesc(service.getId(), Limit.unlimited())
                .isEmpty());
    }
}
